package io.github.cloudiator.examples;/*
 * Copyright (c) 2014-2016 University of Ulm
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import de.uniulm.omi.cloudiator.colosseum.client.Client;
import de.uniulm.omi.cloudiator.colosseum.client.entities.*;
import io.github.cloudiator.examples.internal.CloudHelper;
import io.github.cloudiator.examples.internal.ConfigurationLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by daniel on 27.06.16.
 */
public class MediawikiExample {

    public static void main(String[] args) throws IOException {

        Properties properties = new Properties();
        final FileInputStream fileInputStream =
            new FileInputStream("client-examples/config/example.properties");
        properties.load(fileInputStream);
        fileInputStream.close();

        Client client = ConfigurationLoader.createClient(properties);

        final CloudHelper cloudHelper = new CloudHelper(client);

        final Set<ConfigurationLoader.CloudConfiguration> cloudConfigurations =
            ConfigurationLoader.load(properties);

        for (ConfigurationLoader.CloudConfiguration config : cloudConfigurations) {
            cloudHelper.createCloud(config);
        }

        //create the lifecycle components

        LifecycleComponent loadBalancer = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("LoadBalancer").preInstall("install")
                .postInstall("configure").start("start").build());
        LifecycleComponent wiki = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("MediaWiki").preInstall("install")
                .postInstall("configure").start("start").build());
        LifecycleComponent mariaDB = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("MariaDB").preInstall("install")
                .postInstall("configure").start("start").build());

        //create the application
        Application application = client.controller(Application.class)
            .create(new ApplicationBuilder().name("MediawikiApplication").build());

        //create the virtual machine templates

        VirtualMachineTemplate loadBalancerVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        VirtualMachineTemplate wikiVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        VirtualMachineTemplate mariaDBVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        //create the application components

        ApplicationComponent loadBalancerApplicationComponent =
            client.controller(ApplicationComponent.class).create(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(loadBalancer.getId())
                    .virtualMachineTemplate(loadBalancerVirtualMachineTemplate.getId()).build());

        ApplicationComponent wikiApplicationComponent =
            client.controller(ApplicationComponent.class).create(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(wiki.getId())
                    .virtualMachineTemplate(wikiVirtualMachineTemplate.getId()).build());

        ApplicationComponent mariaDBApplicationComponent =
            client.controller(ApplicationComponent.class).create(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(mariaDB.getId())
                    .virtualMachineTemplate(mariaDBVirtualMachineTemplate.getId()).build());

        //create the ports

        //database
        final PortProvided mariadbprov = client.controller(PortProvided.class).create(
            new PortProvidedBuilder().name("MARIADBPROV")
                .applicationComponent(mariaDBApplicationComponent.getId()).port(3306).build());
        // wiki
        final PortProvided wikiprov = client.controller(PortProvided.class).create(
            new PortProvidedBuilder().name("WIKIPROV")
                .applicationComponent(wikiApplicationComponent.getId()).port(80).build());
        final PortRequired wikireqmariadb = client.controller(PortRequired.class).create(
            new PortRequiredBuilder().name("WIKIREQMARIADB")
                .applicationComponent(wikiApplicationComponent.getId()).isMandatory(true).build());
        // lb
        final PortProvided lbprov = client.controller(PortProvided.class).create(
            new PortProvidedBuilder().name("LBPROV")
                .applicationComponent(loadBalancerApplicationComponent.getId()).port(80).build());
        final PortRequired loadbalancerreqwiki = client.controller(PortRequired.class).create(
            new PortRequiredBuilder().name("LOADBALANCERREQWIKI")
                .applicationComponent(loadBalancerApplicationComponent.getId()).isMandatory(false)
                .updateAction("update").build());

        //create the communication

        // wiki communicates with database
        final Communication wikiWithDB = client.controller(Communication.class).create(
            new CommunicationBuilder().providedPort(mariadbprov.getId())
                .requiredPort(wikireqmariadb.getId()).build());
        //lb communicates with wiki
        final Communication lbWithWiki = client.controller(Communication.class).create(
            new CommunicationBuilder().providedPort(wikiprov.getId())
                .requiredPort(loadbalancerreqwiki.getId()).build());

        // create the virtual machines

        final VirtualMachine mariaDBVM = client.controller(VirtualMachine.class).create(
            VirtualMachineBuilder.of(mariaDBVirtualMachineTemplate).name("mariaDBVM").build());

        final VirtualMachine wikiVM = client.controller(VirtualMachine.class)
            .create(VirtualMachineBuilder.of(wikiVirtualMachineTemplate).name("wikiVM").build());

        final VirtualMachine lbVM = client.controller(VirtualMachine.class).create(
            VirtualMachineBuilder.of(loadBalancerVirtualMachineTemplate).name("lbVM").build());

    }

    private static ConfigurationLoader.CloudConfiguration random(
        Set<ConfigurationLoader.CloudConfiguration> cloudConfigurations) {
        checkState(!cloudConfigurations.isEmpty());
        final ArrayList<ConfigurationLoader.CloudConfiguration> list =
            new ArrayList<>(cloudConfigurations);
        Collections.shuffle(list);
        return list.get(0);
    }

}
