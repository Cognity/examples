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
import de.uniulm.omi.cloudiator.colosseum.client.entities.internal.KeyValue;
import io.github.cloudiator.examples.internal.CloudHelper;
import io.github.cloudiator.examples.internal.ConfigurationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.*;

/**
 * Created by daniel on 27.06.16.
 */
public class MediawikiExample {

    private enum LB {
        HAPROXY, NGINX
    }


    private static final LB lb = LB.HAPROXY;

    private static final boolean instances = false;
    private static final boolean monitoringEnabled = true;
    private static final boolean cleanup = false;


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

        final LifecycleComponent loadBalancer;
        switch (lb) {
            case HAPROXY:
                loadBalancer = client.controller(LifecycleComponent.class).updateOrCreate(
                    new LifecycleComponentBuilder().name("LoadBalancer").preInstall(downloadCommand)
                        .install("./mediawiki-tutorial/scripts/lance/haproxy.sh install")
                        .start("./mediawiki-tutorial/scripts/lance/haproxy.sh startBlocking")
                        .build());
                break;
            case NGINX:
                loadBalancer = client.controller(LifecycleComponent.class).updateOrCreate(
                    new LifecycleComponentBuilder().name("LoadBalancer").preInstall(downloadCommand)
                        .install("./mediawiki-tutorial/scripts/lance/nginx.sh install")
                        .start("./mediawiki-tutorial/scripts/lance/nginx.sh startBlocking")
                        .build());
                break;
            default:
                throw new AssertionError();
        }

        LifecycleComponent wiki = client.controller(LifecycleComponent.class).updateOrCreate(
            new LifecycleComponentBuilder().name("MediaWiki").preInstall(downloadCommand)
                .install("./mediawiki-tutorial/scripts/lance/mediawiki.sh install")
                .postInstall("./mediawiki-tutorial/scripts/lance/mediawiki.sh configure")
                .start("./mediawiki-tutorial/scripts/lance/mediawiki.sh startBlocking").build());

        LifecycleComponent mariaDB = client.controller(LifecycleComponent.class).updateOrCreate(
            new LifecycleComponentBuilder().name("MariaDB").preInstall(downloadCommand)
                .install("./mediawiki-tutorial/scripts/lance/mariaDB.sh install")
                .postInstall("./mediawiki-tutorial/scripts/lance/mariaDB.sh configure")
                .start("./mediawiki-tutorial/scripts/lance/mariaDB.sh startBlocking").build());

        //create the application
        Application application = client.controller(Application.class)
            .updateOrCreate(new ApplicationBuilder().name("MediawikiApplication").build());

        //create the virtual machine templates

        VirtualMachineTemplate loadBalancerVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        VirtualMachineTemplate wikiVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        VirtualMachineTemplate mariaDBVirtualMachineTemplate =
            cloudHelper.createTemplate(random(cloudConfigurations));

        //create the application components

        ApplicationComponent loadBalancerApplicationComponent =
            client.controller(ApplicationComponent.class).updateOrCreate(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(loadBalancer.getId())
                    .virtualMachineTemplate(loadBalancerVirtualMachineTemplate.getId()).build());

        ApplicationComponent wikiApplicationComponent =
            client.controller(ApplicationComponent.class).updateOrCreate(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(wiki.getId())
                    .virtualMachineTemplate(wikiVirtualMachineTemplate.getId()).build());

        ApplicationComponent mariaDBApplicationComponent =
            client.controller(ApplicationComponent.class).updateOrCreate(
                new ApplicationComponentBuilder().application(application.getId())
                    .component(mariaDB.getId())
                    .virtualMachineTemplate(mariaDBVirtualMachineTemplate.getId()).build());

        //create the ports

        //database
        final PortProvided mariadbprov = client.controller(PortProvided.class).updateOrCreate(
            new PortProvidedBuilder().name("MARIADBPROV")
                .applicationComponent(mariaDBApplicationComponent.getId()).port(3306).build());
        // wiki
        final PortProvided wikiprov = client.controller(PortProvided.class).updateOrCreate(
            new PortProvidedBuilder().name("WIKIPROV")
                .applicationComponent(wikiApplicationComponent.getId()).port(80).build());
        final PortRequired wikireqmariadb = client.controller(PortRequired.class).updateOrCreate(
            new PortRequiredBuilder().name("WIKIREQMARIADB")
                .applicationComponent(wikiApplicationComponent.getId()).isMandatory(true).build());
        // lb
        final PortProvided lbprov = client.controller(PortProvided.class).updateOrCreate(
            new PortProvidedBuilder().name("LBPROV")
                .applicationComponent(loadBalancerApplicationComponent.getId()).port(80).build());


        PortRequired loadbalancerreqwiki;
        switch (lb) {
            case NGINX:
                loadbalancerreqwiki = client.controller(PortRequired.class).updateOrCreate(
                    new PortRequiredBuilder().name("LOADBALANCERREQWIKI")
                        .applicationComponent(loadBalancerApplicationComponent.getId())
                        .isMandatory(false)
                        .updateAction("./mediawiki-tutorial/scripts/lance/nginx.sh configure")
                        .build());
                break;
            case HAPROXY:
                loadbalancerreqwiki = client.controller(PortRequired.class).updateOrCreate(
                    new PortRequiredBuilder().name("LOADBALANCERREQWIKI")
                        .applicationComponent(loadBalancerApplicationComponent.getId())
                        .isMandatory(false)
                        .updateAction("./mediawiki-tutorial/scripts/lance/haproxy.sh configure")
                        .build());
                break;
            default:
                throw new AssertionError("unknown lb");
        }


        //create the communication

        // wiki communicates with database
        final Communication wikiWithDB = client.controller(Communication.class).updateOrCreate(
            new CommunicationBuilder().providedPort(mariadbprov.getId())
                .requiredPort(wikireqmariadb.getId()).build());
        //lb communicates with wiki
        final Communication lbWithWiki = client.controller(Communication.class).updateOrCreate(
            new CommunicationBuilder().providedPort(wikiprov.getId())
                .requiredPort(loadbalancerreqwiki.getId()).build());

        final TemplateOptions templateOptions = client.controller(TemplateOptions.class).create(
            new TemplateOptionsBuilder().addTag("started_by", "colosseum_example")
                .userData("myUserData").build());


        if(!instances) {
            System.exit(0);
        }

        // create the virtual machines
        Random random = new Random();

        final VirtualMachine mariaDBVM = client.controller(VirtualMachine.class).create(
            VirtualMachineBuilder.of(mariaDBVirtualMachineTemplate)
                .name("mariaDBVM" + random.nextInt(100)).templateOptions(templateOptions.getId())
                .build());

        final VirtualMachine wikiVM = client.controller(VirtualMachine.class).create(
            VirtualMachineBuilder.of(wikiVirtualMachineTemplate)
                .name("wikiVM" + random.nextInt(100)).build());

        final VirtualMachine lbVM = client.controller(VirtualMachine.class).create(
            VirtualMachineBuilder.of(loadBalancerVirtualMachineTemplate)
                .name("lbVM" + random.nextInt(100)).build());

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

        if (monitoringEnabled) {

            /**
             * Windows and schedules
             */
            final Schedule tenSeconds = client.controller(Schedule.class).updateOrCreate(
                new ScheduleBuilder().interval(10L).timeUnit(TimeUnit.SECONDS).build());
            final TimeWindow minuteWindow = client.controller(TimeWindow.class).updateOrCreate(
                new TimeWindowBuilder().interval(1L).timeUnit(TimeUnit.MINUTES).build());
            final TimeWindow tenSecondWindow = client.controller(TimeWindow.class).updateOrCreate(
                new TimeWindowBuilder().interval(10L).timeUnit(TimeUnit.SECONDS).build());
            final FormulaQuantifier relativeOneFormulaQuantifier =
                client.controller(FormulaQuantifier.class).updateOrCreate(
                    new FormulaQuantifierBuilder().relative(true).value(1.0).build());

            /**
             * Scaling rules
             */
            final ComponentHorizontalOutScalingAction scaleWiki =
                client.controller(ComponentHorizontalOutScalingAction.class).updateOrCreate(
                    new ComponentHorizontalOutScalingActionBuilder().amount(1L)
                        .applicationComponent(wikiApplicationComponent.getId()).count(0L).max(5L)
                        .min(1L).build());



            /**
             * Sensors
             */
            final SensorDescription cpuUsageDescription = client.controller(SensorDescription.class)
                .updateOrCreate(new SensorDescriptionBuilder()
                    .className("de.uniulm.omi.cloudiator.visor.sensors.SystemCpuUsageSensor")
                    .isVmSensor(true).metricName("wikiCpuUsage").build());
            final SensorDescription apacheRequestDescription =
                client.controller(SensorDescription.class).updateOrCreate(
                    new SensorDescriptionBuilder().className(
                        "de.uniulm.omi.cloudiator.visor.sensors.apache.ApacheStatusSensor")
                        .isVmSensor(true).metricName("apacheRequestsPerSecond").build());

            final SensorConfigurations cpuUsageConfiguration =
                client.controller(SensorConfigurations.class)
                    .updateOrCreate(new SensorConfigurationsBuilder().build());
            final SensorConfigurations apacheRequestConfiguration =
                client.controller(SensorConfigurations.class).updateOrCreate(
                    new SensorConfigurationsBuilder()
                        .addConfig(KeyValue.of("apache.status.metric", "CURRENT_REQ_PER_SEC"))
                        .build());


            final RawMonitor wikiCPUUsage = client.controller(RawMonitor.class).updateOrCreate(
                new RawMonitorBuilder().component(wiki.getId()).schedule(tenSeconds.getId())
                    .sensorConfigurations(cpuUsageConfiguration.getId())
                    .sensorDescription(cpuUsageDescription.getId()).build());
            final RawMonitor apacheRequest = client.controller(RawMonitor.class).updateOrCreate(
                new RawMonitorBuilder().component(wiki.getId()).schedule(tenSeconds.getId())
                    .sensorConfigurations(apacheRequestConfiguration.getId())
                    .sensorDescription(apacheRequestDescription.getId()).build());

            final ComposedMonitor averageWikiCpuUsage1Minute =
                client.controller(ComposedMonitor.class).updateOrCreate(
                    new ComposedMonitorBuilder().addMonitor(wikiCPUUsage.getId())
                        .window(minuteWindow.getId()).flowOperator(FlowOperator.MAP)
                        .function(FormulaOperator.AVG)
                        .quantifier(relativeOneFormulaQuantifier.getId())
                        .schedule(tenSeconds.getId()).build());

            final ConstantMonitor thresholdMonitor = client.controller(ConstantMonitor.class)
                .updateOrCreate(new ConstantMonitorBuilder().value(70d).build());

            final ComposedMonitor averageWikiCpuUsageIsAboveThreshold =
                client.controller(ComposedMonitor.class).updateOrCreate(
                    new ComposedMonitorBuilder().addMonitor(averageWikiCpuUsage1Minute.getId())
                        .addMonitor(thresholdMonitor.getId()).schedule(tenSeconds.getId())
                        .window(tenSecondWindow.getId()).flowOperator(FlowOperator.MAP)
                        .quantifier(relativeOneFormulaQuantifier.getId())
                        .function(FormulaOperator.GTE).build());

            final ComposedMonitor countWikiCpuUsageIsAboveThreshold =
                client.controller(ComposedMonitor.class).updateOrCreate(new ComposedMonitorBuilder()
                    .addMonitor(averageWikiCpuUsageIsAboveThreshold.getId())
                    .schedule(tenSeconds.getId()).window(tenSecondWindow.getId())
                    .flowOperator(FlowOperator.REDUCE).function(FormulaOperator.SUM)
                    .quantifier(relativeOneFormulaQuantifier.getId())
                    .addScalingAction(scaleWiki.getId()).build());

            final MonitorSubscription atLeastOneWikiCpuUsageisAboveThreshold =
                client.controller(MonitorSubscription.class).updateOrCreate(
                    new MonitorSubscriptionBuilder().type(SubscriptionType.SCALING)
                        .monitor(countWikiCpuUsageIsAboveThreshold.getId())
                        .filterType(FilterType.GTE).filterValue(1d)
                        .endpoint("http://localhost:9000/api").build());
        }

        if (cleanup) {
            client.controller(Instance.class).delete(lbInstance);
            client.controller(Instance.class).delete(wikiInstance);
            client.controller(Instance.class).delete(dbInstance);

            client.controller(VirtualMachine.class).delete(lbVM);
            client.controller(VirtualMachine.class).delete(wikiVM);
            client.controller(VirtualMachine.class).delete(mariaDBVM);
        }
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



