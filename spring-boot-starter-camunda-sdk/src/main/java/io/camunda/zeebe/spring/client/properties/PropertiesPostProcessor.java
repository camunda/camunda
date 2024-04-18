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
      ConfigurableEnvironment environment, SpringApplication application) {
    try {
      ClientMode clientMode = environment.getProperty("camunda.client.mode", ClientMode.class);
      if (clientMode == null) {
        return;
      }
      YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
      String propertiesFile = determinePropertiesFile(clientMode);
      ClassPathResource resource = new ClassPathResource(propertiesFile);
      List<PropertySource<?>> props = loader.load(propertiesFile, resource);
      for (PropertySource<?> prop : props) {
        environment.getPropertySources().addLast(prop); // lowest priority
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error while post processing camunda properties", e);
    }
  }

  private String determinePropertiesFile(ClientMode clientMode) {
    switch (clientMode) {
      case oidc -> {
        return "application-camunda-oidc.yaml";
      }
      case saas -> {
        return "application-camunda-saas.yaml";
      }
      case simple -> {
        return "application-camunda-simple.yaml";
      }
    }
    throw new IllegalStateException("Unknown client mode " + clientMode);
  }
}
