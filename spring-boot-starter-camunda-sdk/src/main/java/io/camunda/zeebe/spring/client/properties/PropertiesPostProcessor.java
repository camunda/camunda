package io.camunda.zeebe.spring.client.properties;

import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public class PropertiesPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    try {
      final ClientMode clientMode =
          environment.getProperty("camunda.client.mode", ClientMode.class);
      if (clientMode == null) {
        return;
      }
      final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
      final String propertiesFile = determinePropertiesFile(clientMode);
      final ClassPathResource resource = new ClassPathResource(propertiesFile);
      final List<PropertySource<?>> props = loader.load(propertiesFile, resource);
      for (final PropertySource<?> prop : props) {
        environment.getPropertySources().addLast(prop); // lowest priority
      }
    } catch (final Exception e) {
      throw new IllegalStateException("Error while post processing camunda properties", e);
    }
  }

  private String determinePropertiesFile(final ClientMode clientMode) {
    switch (clientMode) {
      case oidc -> {
        return "application-camunda-oidc.yaml";
      }
      case saas -> {
        return "application-camunda-saas.yaml";
      }
      default -> {
        throw new IllegalStateException("Unknown client mode " + clientMode);
      }
    }
  }
}
