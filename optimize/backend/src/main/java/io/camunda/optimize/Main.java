package io.camunda.optimize;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.tomcat.LoggingConfigurationReader;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
@SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
public class Main {

  public static void main(final String[] args) {
    new LoggingConfigurationReader().defineLog4jLoggingConfiguration();

    final ConfigurationService configurationService = ConfigurationService.createDefault();
    final SpringApplication optimize = new SpringApplication(Main.class);

    final Map<String, Object> defaultProperties = new HashMap<>();
    defaultProperties.put(ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort());

    // Import extra Spring config from the config dir (e.g. mounted by Helm extraConfiguration)
    Main.putSystemPropertyIfAbsent("spring.config.import", "optional:file:./config/");

    optimize.setDefaultProperties(defaultProperties);
    optimize.run(args);
  }

  /**
   * Sets system properties only if they haven't been set already, allowing users to override them
   * via CLI or other means
   */
  public static void putSystemPropertyIfAbsent(final String key, final String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }
}
