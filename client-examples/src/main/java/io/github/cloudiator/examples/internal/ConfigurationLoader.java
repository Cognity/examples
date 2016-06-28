package io.github.cloudiator.examples.internal;

import com.google.common.base.Optional;
import de.uniulm.omi.cloudiator.colosseum.client.Client;
import de.uniulm.omi.cloudiator.colosseum.client.ClientBuilder;



import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

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
            builder.apiInternalProvider(config.getString(cloud + ".api.interalProviderName").get());
            //Todo...
            configurations.add(builder.createCloudConfiguration());
        }

        return configurations;
    }

    public interface Configuration {
        Optional<String> getString(String key);

        Optional<List<String>> loadList(String key);

        Optional<Long> getLong(String key);
    }


    static class ConfigurationImpl implements Configuration {

        private final Properties properties;

        ConfigurationImpl(Properties properties) {
            this.properties = properties;
        }

        @Override public Optional<String> getString(String key) {
            return Optional.fromNullable(properties.getProperty(key));
        }

        @Override public Optional<List<String>> loadList(String key) {
            Optional<String> value = getString(key);
            if (!value.isPresent()) {
                return Optional.absent();
            }
            return Optional.of(Arrays.asList(properties.getProperty("clouds").split(",")));
        }

        @Override public Optional<Long> getLong(String key) {
            return Optional.fromNullable(Long.valueOf(properties.getProperty(key)));
        }
    }


    public static class CloudConfiguration {

        private final String name;
        private final String endpoint;
        private final String credentialUsername;
        private final String credentialPassword;
        private final String apiName;
        private final String apiInternalProvider;
        private final long hardwareId;
        private final long locationId;
        private final long imageId;
        private final String imageLoginName;
        private final String imageOperatingSystem;
        private final Map<String, String> properties;


        public CloudConfiguration(String name, String endpoint, String credentialUsername,
            String credentialPassword, String apiName, String apiInternalProvider, long hardwareId,
            long locationId, long imageId, String imageLoginName, String imageOperatingSystem,
            Map<String, String> properties) {

            checkNotNull(name);
            this.name = name;
            this.endpoint = endpoint;
            this.credentialUsername = credentialUsername;
            this.credentialPassword = credentialPassword;
            this.apiName = apiName;
            this.apiInternalProvider = apiInternalProvider;
            this.hardwareId = hardwareId;
            this.locationId = locationId;
            this.imageId = imageId;
            this.imageLoginName = imageLoginName;
            this.imageOperatingSystem = imageOperatingSystem;
            this.properties = properties;
        }

        public String getName() {
            return name;
        }

        public String getEndpoint() {
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

        public long getHardwareId() {
            return hardwareId;
        }

        public long getLocationId() {
            return locationId;
        }

        public long getImageId() {
            return imageId;
        }

        public String getImageLoginName() {
            return imageLoginName;
        }

        public String getImageOperatingSystem() {
            return imageOperatingSystem;
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
