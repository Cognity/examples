/*
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

package io.github.cloudiator.examples.internal;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import de.uniulm.omi.cloudiator.colosseum.client.Client;
import de.uniulm.omi.cloudiator.colosseum.client.entities.*;
import de.uniulm.omi.cloudiator.common.os.OperatingSystemFamily;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by daniel on 27.06.16.
 */
public class CloudHelper {

    private final static long WAIT_TIMEOUT_MIN = 3;

    private Set<CloudConfigurationVisitor> visitors;
    private final Client client;

    public CloudHelper(Client client) {
        this.client = client;
        visitors = Sets.newLinkedHashSet();
        visitors.add(new CreateApi(client));
        visitors.add(new CreateCloud(client));
        visitors.add(new CreateCloudProperties(client));
        visitors.add(new CreateCloudCredential(client));
        visitors.add(new UpdateImageLogin(client));
        visitors.add(new UpdateImageOs(client));
        //add properties

    }

    public void createCloud(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
        for (CloudConfigurationVisitor visitor : visitors) {
            visitor.visit(cloudConfiguration);
        }
    }

    public VirtualMachineTemplate createTemplate(
            final ConfigurationLoader.CloudConfiguration cloudConfiguration) {

        final Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
            @Override
            public boolean apply(@Nullable Cloud input) {
                checkNotNull(input);
                return input.getName().equals(cloudConfiguration.getName());
            }
        }).get();
        Location location =
                client.controller(Location.class).waitAndGetSingle(new Predicate<Location>() {
                    @Override
                    public boolean apply(@Nullable Location input) {
                        checkNotNull(input);
                        return cloudConfiguration.getLocationId().equals(input.getProviderId()) && input
                                .getCloud().equals(cloud.getId());
                    }
                }, WAIT_TIMEOUT_MIN, TimeUnit.MINUTES).get();
        Image image = client.controller(Image.class).waitAndGetSingle(new Predicate<Image>() {
            @Override
            public boolean apply(@Nullable Image input) {
                checkNotNull(input);
                return cloudConfiguration.getImageId().equals(input.getProviderId()) && input
                        .getCloud().equals(cloud.getId());
            }
        }, WAIT_TIMEOUT_MIN, TimeUnit.MINUTES).get();
        Hardware hardware =
                client.controller(Hardware.class).waitAndGetSingle(new Predicate<Hardware>() {
                    @Override
                    public boolean apply(@Nullable Hardware input) {
                        checkNotNull(input);
                        return cloudConfiguration.getHardwareId().equals(input.getProviderId()) && input
                                .getCloud().equals(cloud.getId());
                    }
                }, WAIT_TIMEOUT_MIN, TimeUnit.MINUTES).get();


        return client.controller(VirtualMachineTemplate.class).updateOrCreate(
                new VirtualMachineTemplateBuilder().cloud(cloud.getId()).location(location.getId())
                        .image(image.getId()).hardware(hardware.getId()).build());
    }

    interface CloudConfigurationVisitor {
        void visit(ConfigurationLoader.CloudConfiguration cloudConfiguration);
    }


    class CreateApi implements CloudConfigurationVisitor {
        private final Client client;

        CreateApi(Client client) {
            this.client = client;
        }

        @Override
        public void visit(ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            client.controller(Api.class).updateOrCreate(
                    new ApiBuilder().name(cloudConfiguration.getApiName())
                            .internalProviderName(cloudConfiguration.getApiInternalProvider()).build());
        }
    }


    class CreateCloud implements CloudConfigurationVisitor {

        private final Client client;

        CreateCloud(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            Api api = client.controller(Api.class).getSingle(new Predicate<Api>() {
                @Override
                public boolean apply(@Nullable Api input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getApiName());
                }
            }).get();
            client.controller(Cloud.class).updateOrCreate(
                    new CloudBuilder().api(api.getId()).endpoint(cloudConfiguration.getEndpoint())
                            .name(cloudConfiguration.getName()).build());
        }
    }


    private class CreateCloudProperties implements CloudConfigurationVisitor {

        private final Client client;

        CreateCloudProperties(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            final Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
                @Override
                public boolean apply(@Nullable Cloud input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getName());
                }
            }).get();

            for (Map.Entry<String, String> entry : cloudConfiguration.getProperties().entrySet()) {
                client.controller(CloudProperty.class).updateOrCreate(
                        new CloudPropertyBuilder().cloud(cloud.getId()).key(entry.getKey())
                                .value(entry.getValue()).build());
            }
        }
    }


    private class CreateCloudCredential implements CloudConfigurationVisitor {

        private final Client client;

        public CreateCloudCredential(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            // todo workaround for cloud credential requiring a tenant...
            Tenant tenant = client.controller(Tenant.class).getSingle(new Predicate<Tenant>() {
                @Override
                public boolean apply(@Nullable Tenant input) {
                    checkNotNull(input);
                    return input.getName().equals("admin");
                }
            }).get();

            Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
                @Override
                public boolean apply(@Nullable Cloud input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getName());
                }
            }).get();
            client.controller(CloudCredential.class).updateOrCreate(
                    new CloudCredentialBuilder().cloud(cloud.getId())
                            .secret(cloudConfiguration.getCredentialPassword())
                            .user(cloudConfiguration.getCredentialUsername()).tenant(tenant.getId())
                            .build());
        }
    }


    private class UpdateImageLogin implements CloudConfigurationVisitor {

        private final Client client;

        public UpdateImageLogin(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            final Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
                @Override
                public boolean apply(@Nullable Cloud input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getName());
                }
            }).get();

            Image image = client.controller(Image.class).waitAndGetSingle(new Predicate<Image>() {
                @Override
                public boolean apply(@Nullable Image input) {
                    checkNotNull(input);
                    return input.getCloud().equals(cloud.getId()) && cloudConfiguration.getImageId()
                            .equals(input.getProviderId());
                }
            }, 3, TimeUnit.MINUTES).get();

            if (cloudConfiguration.getImageLoginName() != null) {
                image.setDefaultLoginUsername(cloudConfiguration.getImageLoginName());
            }

            client.controller(Image.class).update(image);
        }
    }

    private class UpdateImageOs implements CloudConfigurationVisitor {

        private final Client client;

        public UpdateImageOs(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            final Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
                @Override
                public boolean apply(@Nullable Cloud input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getName());
                }
            }).get();

            Image image = client.controller(Image.class).waitAndGetSingle(new Predicate<Image>() {
                @Override
                public boolean apply(@Nullable Image input) {
                    checkNotNull(input);
                    return input.getCloud().equals(cloud.getId()) && cloudConfiguration.getImageId()
                            .equals(input.getProviderId());
                }
            }, 3, TimeUnit.MINUTES).get();

            OperatingSystem os = client.controller(OperatingSystem.class).get(image.getOperatingSystem());

            if (os.getOperatingSystemFamily() == null || os.getOperatingSystemFamily().equals(OperatingSystemFamily.UNKNOWN)) {
                if (cloudConfiguration.operatingSystemVendor() == null) {
                    throw new IllegalStateException("Could not resolve vendor of image. Not auto discovered and not configured!.");
                }
                os.setOperatingSystemFamily(OperatingSystemFamily.valueOf(cloudConfiguration.operatingSystemVendor()));
            }

            client.controller(OperatingSystem.class).update(os);
        }
    }


}
