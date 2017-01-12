package io.github.cloudiator.examples;

import de.uniulm.omi.cloudiator.colosseum.client.Client;
import de.uniulm.omi.cloudiator.colosseum.client.ClientBuilder;
import de.uniulm.omi.cloudiator.colosseum.client.entities.*;
import io.github.cloudiator.examples.internal.KairosDbConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A very simple example of execution a cloud bursting.
 */
public class CloudBurstingExample {

    private final static long THRESHOLD = 50;

    private final static String METRICNAME = "cpu_usage";

    private final static String COLOSSEUM_ENDPOINT = "http://localhost:9000/api";
    private final static String COLOSSEUM_USERNAME = "john.doe@example.com";
    private final static String COLOSSEUM_PASSWORD = "admin";
    private final static String COLOSSEUM_TENANT = "admin";

    private final static String COMPONENT_NAME = "QuorumAppServerComponent";
    private final static String VIRTUALMACHINE_NAME = "EC2StackWindowsVMVMInstance";

    private final static String API_NAME = "aws-ec2";
    private final static String API_DRIVER = "aws-ec2";
    private final static String CLOUD_NAME = "aws-ec2";

    private final static String CLOUDCREDENTIAL_USERNAME = "username";
    private final static String CLOUDCREDENTIAL_SECRET =
        "secret";

    private final static String IMAGE_ID = "eu-west-1/ami-5d57032e";
    private final static String LOCATION_ID = "eu-west-1";
    private final static String HARDWARE_ID = "m3.medium";

    private final static String IMAGE_LOGIN = "cloudiator";
    private final static String IMAGE_PASSWORD = "Admin1";

    private final static String AMI_FILTER = "image-id=ami-5d57032e";
    private final static String AMI_CC_FILTER = "image-id=ami-5d57032e";



    public static void main(String[] args) {

        Client client = ClientBuilder.getNew()
            .credentials(COLOSSEUM_USERNAME, COLOSSEUM_TENANT, COLOSSEUM_PASSWORD)
            .url(COLOSSEUM_ENDPOINT).build();

        ApplicationComponent applicationComponent = null;
        boolean burst = false;

        while (!burst) {
            try {
                final LifecycleComponent lifecycleComponent =
                    client.controller(LifecycleComponent.class)
                        .waitAndGetSingle(input -> COMPONENT_NAME.equals(input.getName()),
                            365, TimeUnit.DAYS).get();

                applicationComponent = client.controller(ApplicationComponent.class)
                    .waitAndGetSingle(
                        input -> lifecycleComponent.getId().equals(input.getComponent()), 365,
                        TimeUnit.DAYS).get();

                final ApplicationComponent finalApplicationComponent = applicationComponent;
                final Instance instance = client.controller(Instance.class).waitAndGetSingle(
                    input -> finalApplicationComponent.getId()
                        .equals(input.getApplicationComponent()), 365, TimeUnit.DAYS).get();

                final VirtualMachine virtualMachine = client.controller(VirtualMachine.class)
                    .waitAndGetSingle(input -> instance.getVirtualMachine().equals(input.getId()),
                        365, TimeUnit.DAYS).get();

                final IpAddress ipAddress = client.controller(IpAddress.class).waitAndGetSingle(
                    input -> virtualMachine.getId().equals(input.getVirtualMachine()) && "PUBLIC"
                        .equals(input.getIpType()), 365, TimeUnit.DAYS).get();

                KairosDbConnection kairos = new KairosDbConnection(ipAddress.getIp(), 8080);
                List<Double> aggregatedValues =
                    kairos.getAggregatedValue(METRICNAME, new ArrayList<>(), 1200);

                for (Double value : aggregatedValues) {
                    if (value > THRESHOLD) {
                        burst = true;
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

        }

        System.out.println("Trigger Bursting");

        //create the API
        Api api = client.controller(Api.class).updateOrCreate(
            new ApiBuilder().name(API_NAME).internalProviderName(API_DRIVER).build());

        //create the cloud
        Cloud cloud = client.controller(Cloud.class)
            .updateOrCreate(new CloudBuilder().api(api.getId()).name(CLOUD_NAME).build());

        //create cloud property for the ami filter
        CloudProperty amiFilter = client.controller(CloudProperty.class).updateOrCreate(
            new CloudPropertyBuilder().cloud(cloud.getId()).key("sword.ec2.ami.query")
                .value(AMI_FILTER).build());

        //create the cloud property for the ami cc filter
        CloudProperty amiCCFilter = client.controller(CloudProperty.class).updateOrCreate(
            new CloudPropertyBuilder().cloud(cloud.getId()).key("sword.ec2.ami.cc.query")
                .value(AMI_CC_FILTER).build());

        //create the cloud credential
        CloudCredential cloudCredential = client.controller(CloudCredential.class).updateOrCreate(
            new CloudCredentialBuilder().cloud(cloud.getId()).secret(CLOUDCREDENTIAL_SECRET)
                .tenant(1L).user(CLOUDCREDENTIAL_USERNAME).build());

        //wait for the location
        Location location = client.controller(Location.class)
            .waitAndGetSingle(input -> LOCATION_ID.equals(input.getSwordId()), 3,
                TimeUnit.MINUTES).get();

        //wait for the hardware
        Hardware hardware = client.controller(Hardware.class)
            .waitAndGetSingle(input -> HARDWARE_ID.equals(input.getSwordId()), 3,
                TimeUnit.MINUTES).get();

        //wait for the image
        Image image = client.controller(Image.class)
            .waitAndGetSingle(input -> IMAGE_ID.equals(input.getSwordId()), 3, TimeUnit.MINUTES)
            .get();

        //update the image
        image.setDefaultLoginPassword(IMAGE_PASSWORD);
        image.setDefaultLoginUsername(IMAGE_LOGIN);

        client.controller(Image.class).update(image);

        //start a new virtual machine
        VirtualMachine virtualMachine = client.controller(VirtualMachine.class).create(
            new VirtualMachineBuilder().cloud(cloud.getId()).hardware(hardware.getId())
                .image(image.getId()).location(location.getId())
                .name(VIRTUALMACHINE_NAME + System.currentTimeMillis()).build());


        //get application instance
        ApplicationInstance applicationInstance =
            client.controller(ApplicationInstance.class).getSingle(input -> true).get();

        //start a new instance
        client.controller(Instance.class).create(
            new InstanceBuilder().applicationComponent(applicationComponent.getId())
                .applicationInstance(applicationInstance.getId())
                .virtualMachine(virtualMachine.getId()).build());


        System.out.println("Started.");
    }
}
