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
import io.github.cloudiator.examples.internal.CloudCreator;
import io.github.cloudiator.examples.internal.ConfigurationLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by daniel on 27.06.16.
 */
public class MediawikiExample {

    public static void main(String[] args) throws IOException {

        Properties properties = new Properties();
        properties.load(new FileInputStream("config/example.properties"));

        Client client = ConfigurationLoader.createClient(properties);

        final CloudCreator cloudCreator = new CloudCreator(client);

        for (ConfigurationLoader.CloudConfiguration config : ConfigurationLoader.load(properties)) {
            cloudCreator.createCloud(config);
        }
    }

}
