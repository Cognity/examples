package io.github.cloudiator.examples.internal;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import de.uniulm.omi.cloudiator.colosseum.client.Client;
import de.uniulm.omi.cloudiator.colosseum.client.ClientBuilder;

import javax.annotation.Nullable;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by daniel on 27.06.16.
 */
public class ConfigurationLoader {

    public static Client createClient(Properties properties) {
        Configuration configuration = new ConfigurationImpl(properties);
        return ClientBuilder.getNew().credentials(configuration.getString("colosseum.user").get(),
            configuration.getString("colosseum.tenant").get(),
            configuration.getString("colosseum.password").get())
            .url(configuration.getString("colosseum.url").get()).build();
    }

    public static Set<CloudConfiguration> load(Properties properties) {

        Configuration config = new ConfigurationImpl(properties);
        Set<String> clouds = new HashSet<>(config.loadList("clouds").get());
        Set<CloudConfiguration> configurations = new HashSet<>(clouds.size());

        for (String cloud : clouds) {
            CloudConfigurationBuilder builder = new CloudConfigurationBuilder();
            builder.name(config.getString(cloud + ".cloud.name").get());
            builder.endpoint(config.getString(cloud + ".cloud.endpoint").get());
            builder
                .credentialUsername(config.getString(cloud + ".cloud.credential.username").get());
            builder
                .credentialPassword(config.getString(cloud + ".cloud.credential.password").get());
            builder.apiName(config.getString(cloud + ".api.name").get());
            builder
                .apiInternalProvider(config.getString(cloud + ".api.internalProviderName").get());
            builder.imageId(config.getString(cloud + ".image.providerId").get());
            builder.imageOperatingSystemVendor(
                config.getString(cloud + ".image.operatingSystemVendor").get());
            builder.locationId(config.getString(cloud + ".location.providerId ").get());
            builder.hardwareId(config.getString(cloud + ".hardware.providerId").get());
            builder.properties(config.loadMap(cloud + ".properties").get());
            //todo check
            configurations.add(builder.createCloudConfiguration());
        }

        return configurations;
    }

    public interface Configuration {

        Optional<String> getString(String key);

        Optional<List<String>> loadList(String key);

        Optional<Map<String, String>> loadMap(String key);
    }


    static class ConfigurationImpl implements Configuration {

        private final Properties properties;

        ConfigurationImpl(Properties properties) {
            this.properties = properties;
        }

        @Override public Optional<String> getString(String key) {
            key = key.trim();
            final String property = properties.getProperty(key);
            return Optional.fromNullable(property);
        }

        @Override public Optional<List<String>> loadList(String key) {
            Optional<String> value = getString(key);
            if (!value.isPresent()) {
                return Optional.absent();
            }
            final String[] strings = properties.getProperty(key).split(",");
            String[] trimmedArray = new String[strings.length];
            for (int i = 0; i < strings.length; i++)
                trimmedArray[i] = strings[i].trim();
            return Optional.of(Arrays.asList(trimmedArray));
        }

        @Override public Optional<Map<String, String>> loadMap(String key) {
            if (!loadList(key).isPresent()) {
                return Optional.absent();
            }
            final List<String> keyValuePairs = loadList(key).get();
            final Map<String, String> map = Maps.newHashMapWithExpectedSize(keyValuePairs.size());
            for (String keyValuePair : keyValuePairs) {
                final String[] strings = keyValuePair.split("=");
                checkState(strings.length == 2);
                map.put(strings[0], strings[1]);
            }
            return Optional.of(map);
        }
    }


    public static class CloudConfiguration {

        private final String name;
        @Nullable private final String endpoint;
        private final String credentialUsername;
        private final String credentialPassword;
        private final String apiName;
        private final String apiInternalProvider;
        private final String hardwareId;
        private final String locationId;
        private final String imageId;
        private final String imageLoginName;
        private final String imageOperatingSystemVendor;
        private final Map<String, String> properties;


        public CloudConfiguration(String name, @Nullable String endpoint, String credentialUsername,
            String credentialPassword, String apiName, String apiInternalProvider,
            String hardwareId, String locationId, String imageId, String imageLoginName,
            String imageOperatingSystemVendor, Map<String, String> properties) {

            checkNotNull(name);
            this.name = name;

            this.endpoint = endpoint;

            checkNotNull(credentialUsername);
            this.credentialUsername = credentialUsername;

            checkNotNull(credentialPassword);
            this.credentialPassword = credentialPassword;

            checkNotNull(apiName);
            this.apiName = apiName;

            checkNotNull(apiInternalProvider);
            this.apiInternalProvider = apiInternalProvider;

            checkNotNull(hardwareId);
            this.hardwareId = hardwareId;

            checkNotNull(locationId);
            this.locationId = locationId;

            checkNotNull(imageId);
            this.imageId = imageId;

            this.imageLoginName = imageLoginName;
            this.imageOperatingSystemVendor = imageOperatingSystemVendor;
            this.properties = properties;
        }

        public String getName() {
            return name;
        }

        @Nullable public String getEndpoint() {
            return endpoint;
        }

        public String getCredentialUsername() {
            return credentialUsername;
        }

        public String getCredentialPassword() {
            return credentialPassword;
        }

        public String getApiName() {
            return apiName;
        }

        public String getApiInternalProvider() {
            return apiInternalProvider;
        }

        public String getHardwareId() {
            return hardwareId;
        }

        public String getLocationId() {
            return locationId;
        }

        public String getImageId() {
            return imageId;
        }

        public String getImageLoginName() {
            return imageLoginName;
        }

        public String getImageOperatingSystemVendor() {
            return imageOperatingSystemVendor;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CloudConfiguration that = (CloudConfiguration) o;

            return getName().equals(that.getName());

        }

        @Override public int hashCode() {
            return getName().hashCode();
        }
    }

}
