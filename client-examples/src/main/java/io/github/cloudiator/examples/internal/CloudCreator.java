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

import javax.annotation.Nullable;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by daniel on 27.06.16.
 */
public class CloudCreator {

    public Set<CloudConfigurationVisitor> visitors;

    public CloudCreator(Client client) {
        visitors = Sets.newHashSet(new CreateApi(client), new CreateCloud(client),
            new CreateCloudCredential(client));
    }

    public void createCloud(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
        for (CloudConfigurationVisitor visitor : visitors) {
            visitor.visit(cloudConfiguration);
        }
    }

    public interface CloudConfigurationVisitor {
        void visit(ConfigurationLoader.CloudConfiguration cloudConfiguration);
    }


    class CreateApi implements CloudConfigurationVisitor {
        private final Client client;

        public CreateApi(Client client) {
            this.client = client;
        }

        @Override public void visit(ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            client.controller(Api.class).create(
                new ApiBuilder().name(cloudConfiguration.getApiName())
                    .internalProviderName(cloudConfiguration.getApiInternalProvider()).build());
        }
    }


    class CreateCloud implements CloudConfigurationVisitor {

        private final Client client;

        public CreateCloud(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            Api api = client.controller(Api.class).getSingle(new Predicate<Api>() {
                @Override public boolean apply(@Nullable Api input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getApiName());
                }
            }).get();
            client.controller(Cloud.class).create(
                new CloudBuilder().api(api.getId()).endpoint(cloudConfiguration.getEndpoint())
                    .name(cloudConfiguration.getName()).build());
        }
    }


    class CreateCloudCredential implements CloudConfigurationVisitor {

        private final Client client;

        public CreateCloudCredential(Client client) {
            this.client = client;
        }

        @Override
        public void visit(final ConfigurationLoader.CloudConfiguration cloudConfiguration) {
            // todo workaround for cloud credential requiring a tenant...
            Tenant tenant = client.controller(Tenant.class).getSingle(new Predicate<Tenant>() {
                @Override public boolean apply(@Nullable Tenant input) {
                    checkNotNull(input);
                    return input.getName().equals("admin");
                }
            }).get();

            Cloud cloud = client.controller(Cloud.class).getSingle(new Predicate<Cloud>() {
                @Override public boolean apply(@Nullable Cloud input) {
                    checkNotNull(input);
                    return input.getName().equals(cloudConfiguration.getApiName());
                }
            }).get();
            client.controller(CloudCredential.class).create(
                new CloudCredentialBuilder().cloud(cloud.getId())
                    .secret(cloudConfiguration.getCredentialPassword())
                    .user(cloudConfiguration.getCredentialUsername()).tenant(tenant.getId())
                    .build());
        }
    }


}
