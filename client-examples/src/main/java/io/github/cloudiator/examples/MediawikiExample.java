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
import de.uniulm.omi.cloudiator.colosseum.client.entities.enums.*;
import io.github.cloudiator.examples.internal.CloudHelper;
import io.github.cloudiator.examples.internal.ConfigurationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.*;

/**
 * Created by daniel on 27.06.16.
 */
public class MediawikiExample {

    public static void main(String[] args) throws IOException {

        final String configFileProperty = System.getProperty("config.file");
        checkArgument(configFileProperty != null,
            "Missing parameter config.file, use -Dconfig.file parameter");
        final File file = new File(configFileProperty);
        checkArgument(file.exists() && file.isFile(), String
            .format("Could not find file %s, check the -Dconfig.file option",
                file.getAbsolutePath()));

        Properties properties = new Properties();
        final FileInputStream fileInputStream = new FileInputStream(file);
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

        String downloadCommand =
            "sudo apt-get -y update && sudo apt-get -y install git && git clone https://github.com/dbaur/mediawiki-tutorial.git";

        LifecycleComponent loadBalancer = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("LoadBalancer").preInstall(downloadCommand)
                .install("./mediawiki-tutorial/scripts/lance/nginx.sh install")
                .start("./mediawiki-tutorial/scripts/lance/nginx.sh startBlocking").build());

        LifecycleComponent wiki = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("MediaWiki").preInstall(downloadCommand)
                .install("./mediawiki-tutorial/scripts/lance/mediawiki.sh install")
                .postInstall("./mediawiki-tutorial/scripts/lance/mediawiki.sh configure")
                .start("./mediawiki-tutorial/scripts/lance/mediawiki.sh startBlocking").build());

        LifecycleComponent mariaDB = client.controller(LifecycleComponent.class).create(
            new LifecycleComponentBuilder().name("MariaDB").preInstall(downloadCommand)
                .install("./mediawiki-tutorial/scripts/lance/mariaDB.sh install")
                .postInstall("./mediawiki-tutorial/scripts/lance/mariaDB.sh configure")
                .start("./mediawiki-tutorial/scripts/lance/mariaDB.sh startBlocking").build());

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
                .updateAction("./mediawiki-tutorial/scripts/lance/nginx.sh configure").build());

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

        // create the application instance
        final ApplicationInstance appInstance = client.controller(ApplicationInstance.class)
            .create(new ApplicationInstanceBuilder().application(application.getId()).build());

        // create the instances

        final Instance lbInstance = client.controller(Instance.class).create(
            new InstanceBuilder().applicationComponent(loadBalancerApplicationComponent.getId())
                .applicationInstance(appInstance.getId()).virtualMachine(lbVM.getId()).build());

        final Instance wikiInstance = client.controller(Instance.class).create(
            new InstanceBuilder().applicationComponent(wikiApplicationComponent.getId())
                .applicationInstance(appInstance.getId()).virtualMachine(wikiVM.getId()).build());

        final Instance dbInstance = client.controller(Instance.class).create(
            new InstanceBuilder().applicationComponent(mariaDBApplicationComponent.getId())
                .applicationInstance(appInstance.getId()).virtualMachine(mariaDBVM.getId())
                .build());

        waitForInstance(client, lbInstance);
        waitForInstance(client, wikiInstance);
        waitForInstance(client, dbInstance);

        final Schedule tenSeconds = client.controller(Schedule.class)
            .create(new ScheduleBuilder().interval(10L).timeUnit(TimeUnit.SECONDS).build());
        final TimeWindow minuteWindow = client.controller(TimeWindow.class)
            .create(new TimeWindowBuilder().interval(1L).timeUnit(TimeUnit.MINUTES).build());
        final FormulaQuantifier relativeOneFormulaQuantifier =
            client.controller(FormulaQuantifier.class)
                .create(new FormulaQuantifierBuilder().relative(true).value(1.0).build());

        final SensorDescription cpuUsageDescription = client.controller(SensorDescription.class)
            .create(new SensorDescriptionBuilder()
                .className("de.uniulm.omi.cloudiator.visor.sensors.SystemCpuUsageSensor")
                .isVmSensor(true).metricName("wikiCpuUsage").build());
        final SensorDescription apacheRequestDescription =
            client.controller(SensorDescription.class).create(new SensorDescriptionBuilder()
                .className("de.uniulm.omi.cloudiator.visor.sensors.apache.ApacheStatusSensor")
                .isVmSensor(true).metricName("apacheRequestsPerSecond").build());


        final SensorConfigurations cpuUsageConfiguration =
            client.controller(SensorConfigurations.class)
                .create(new SensorConfigurationsBuilder().build());
        //todo add keyvalue pairs
        final SensorConfigurations apacheRequestConfiguration =
            client.controller(SensorConfigurations.class)
                .create(new SensorConfigurationsBuilder().build());

        final RawMonitor wikiCPUUsage = client.controller(RawMonitor.class).create(
            new RawMonitorBuilder().component(wiki.getId()).schedule(tenSeconds.getId())
                .sensorConfigurations(cpuUsageConfiguration.getId())
                .sensorDescription(cpuUsageDescription.getId()).build());
        //final RawMonitor apacheRequest = client.controller(RawMonitor.class).create(
        //    new RawMonitorBuilder().component(wiki.getId()).schedule(tenSeconds.getId())
        //        .sensorConfigurations(apacheRequestConfiguration.getId())
        //        .sensorDescription(apacheRequestDescription.getId()).build());

        final ComposedMonitor averageWikiCpuUsage1Minute = client.controller(ComposedMonitor.class)
            .create(new ComposedMonitorBuilder().addMonitor(wikiCPUUsage.getId())
                .window(minuteWindow.getId()).flowOperator(FlowOperator.MAP)
                .function(FormulaOperator.AVG).quantifier(relativeOneFormulaQuantifier.getId())
                .schedule(tenSeconds.getId()).build());

        final ConstantMonitor threshold60 = client.controller(ConstantMonitor.class)
            .create(new ConstantMonitorBuilder().value(60.0).build());

        final ComposedMonitor averageWikiCpuUsageIsAboveThreshold60 =
            client.controller(ComposedMonitor.class).create(
                new ComposedMonitorBuilder().addMonitor(averageWikiCpuUsage1Minute.getId())
                    .addMonitor(threshold60.getId()).schedule(tenSeconds.getId())
                    .window(minuteWindow.getId()).flowOperator(FlowOperator.MAP)
                    .quantifier(relativeOneFormulaQuantifier.getId()).function(FormulaOperator.GTE)
                    .build());

        final ComposedMonitor countWikiCpuUsageIsAboveThreshold60 =
            client.controller(ComposedMonitor.class).create(new ComposedMonitorBuilder()
                .addMonitor(averageWikiCpuUsageIsAboveThreshold60.getId())
                .schedule(tenSeconds.getId()).window(minuteWindow.getId())
                .flowOperator(FlowOperator.REDUCE).function(FormulaOperator.SUM)
                .quantifier(relativeOneFormulaQuantifier.getId()).build());

        final MonitorSubscription atLeastOneWikiCpuUsageisAboveThreshold60 =
            client.controller(MonitorSubscription.class).create(
                new MonitorSubscriptionBuilder().type(SubscriptionType.SCALING)
                    .monitor(countWikiCpuUsageIsAboveThreshold60.getId()).filterType(FilterType.GTE)
                    .filterValue(1.0).endpoint("http://localhost:9000/api").build());
    }

    private static ConfigurationLoader.CloudConfiguration random(
        Set<ConfigurationLoader.CloudConfiguration> cloudConfigurations) {
        checkState(!cloudConfigurations.isEmpty());
        final ArrayList<ConfigurationLoader.CloudConfiguration> list =
            new ArrayList<>(cloudConfigurations);
        Collections.shuffle(list);
        return list.get(0);
    }

    private static void waitForInstance(Client client, Instance instance) {
        while (!RemoteState.OK.equals(instance.getRemoteState())) {
            if (RemoteState.ERROR.equals(instance.getRemoteState())) {
                throw new RuntimeException("Starting of instance failed");
            }
            try {
                Thread.sleep(5000);
                instance = client.controller(Instance.class).get(instance.getId());
                checkNotNull(instance);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}



