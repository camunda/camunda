/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      case selfManaged -> {
        return "application-camunda-self-managed.yaml";
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
