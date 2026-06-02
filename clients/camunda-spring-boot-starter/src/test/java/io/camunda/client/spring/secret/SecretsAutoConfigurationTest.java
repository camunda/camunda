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
package io.camunda.client.spring.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.secret.SecretsClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.secret.SecretResolvingJsonMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SecretsAutoConfigurationTest {

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecretsAutoConfiguration.class))
        .withUserConfiguration(BaseConfig.class);
  }

  @Test
  void shouldNotActivateWhenPropertyMissing() {
    contextRunner()
        .withUserConfiguration(DefaultJsonMapperConfig.class)
        .run(
            context -> {
              final JsonMapper mapper = context.getBean(JsonMapper.class);
              assertThat(mapper).isNotInstanceOf(SecretResolvingJsonMapper.class);
            });
  }

  @Test
  void shouldWrapDefaultJsonMapperWhenEnabled() {
    contextRunner()
        .withPropertyValues("camunda.client.secrets.transparent-resolution=true")
        .withUserConfiguration(DefaultJsonMapperConfig.class)
        .run(
            context -> {
              final JsonMapper mapper = context.getBean(JsonMapper.class);
              assertThat(mapper).isInstanceOf(SecretResolvingJsonMapper.class);
            });
  }

  @Test
  void shouldWrapUserProvidedJsonMapperPreservingItsBehavior() {
    contextRunner()
        .withPropertyValues("camunda.client.secrets.transparent-resolution=true")
        .withUserConfiguration(CustomJsonMapperConfig.class)
        .run(
            context -> {
              final JsonMapper mapper = context.getBean(JsonMapper.class);
              assertThat(mapper).isInstanceOf(SecretResolvingJsonMapper.class);

              // The user's mapper still gets to deserialize first; we should see its marker.
              final Map<String, Object> result = mapper.fromJsonAsMap("{\"x\":1}");
              assertThat(result).containsEntry("__from_custom_mapper", Boolean.TRUE);
            });
  }

  @Test
  void shouldUseUserProvidedSecretsClientWhenDefined() {
    contextRunner()
        .withPropertyValues("camunda.client.secrets.transparent-resolution=true")
        .withUserConfiguration(DefaultJsonMapperConfig.class, CustomSecretsClientConfig.class)
        .run(
            context -> {
              final SecretsClient client = context.getBean(SecretsClient.class);
              assertThat(client).isInstanceOf(StubSecretsClient.class);

              final JsonMapper mapper = context.getBean(JsonMapper.class);
              final Map<String, Object> result =
                  mapper.fromJsonAsMap("{\"token\":\"camunda.secrets.FOO\"}");
              assertThat(result).containsEntry("token", "stub-value");
            });
  }

  @Test
  void shouldNotDoubleWrap() {
    contextRunner()
        .withPropertyValues("camunda.client.secrets.transparent-resolution=true")
        .withUserConfiguration(AlreadyWrappedJsonMapperConfig.class)
        .run(
            context -> {
              final JsonMapper mapper = context.getBean(JsonMapper.class);
              assertThat(mapper).isInstanceOf(SecretResolvingJsonMapper.class);
              assertThat(mapper).isSameAs(context.getBean("jsonMapper"));
            });
  }

  // ---- Test fixtures --------------------------------------------------------

  @Configuration
  static class BaseConfig {
    // A CamundaClient bean is required because the default SecretsClient depends on it (@Lazy).
    @Bean
    CamundaClient camundaClient() {
      return Mockito.mock(CamundaClient.class);
    }
  }

  @Configuration
  static class DefaultJsonMapperConfig {
    @Bean
    JsonMapper jsonMapper() {
      return new CamundaObjectMapper();
    }
  }

  @Configuration
  static class CustomJsonMapperConfig {
    @Bean
    JsonMapper jsonMapper() {
      return new MarkerJsonMapper();
    }
  }

  @Configuration
  static class CustomSecretsClientConfig {
    @Bean
    SecretsClient secretsClient() {
      return new StubSecretsClient();
    }
  }

  @Configuration
  static class AlreadyWrappedJsonMapperConfig {
    @Bean
    JsonMapper jsonMapper() {
      return new SecretResolvingJsonMapper(
          new CamundaObjectMapper(), references -> java.util.Collections.emptyMap());
    }
  }

  /** Stamps every deserialized map with a marker so we can prove the wrapper delegates to it. */
  static class MarkerJsonMapper implements JsonMapper {
    private final JsonMapper delegate = new CamundaObjectMapper();

    @Override
    public <T> T fromJson(final String json, final Class<T> typeClass) {
      return delegate.fromJson(json, typeClass);
    }

    @Override
    public Map<String, Object> fromJsonAsMap(final String json) {
      final Map<String, Object> map = new HashMap<>(delegate.fromJsonAsMap(json));
      map.put("__from_custom_mapper", Boolean.TRUE);
      return map;
    }

    @Override
    public Map<String, String> fromJsonAsStringMap(final String json) {
      return delegate.fromJsonAsStringMap(json);
    }

    @Override
    public String toJson(final Object value) {
      return delegate.toJson(value);
    }

    @Override
    public String validateJson(final String propertyName, final String jsonInput) {
      return delegate.validateJson(propertyName, jsonInput);
    }

    @Override
    public String validateJson(final String propertyName, final InputStream jsonInput)
        throws InternalClientException {
      return delegate.validateJson(propertyName, jsonInput);
    }
  }

  static class StubSecretsClient implements SecretsClient {
    @Override
    public Map<String, String> resolve(final List<String> references) {
      final Map<String, String> out = new HashMap<>();
      for (final String ref : references) {
        out.put(ref, "stub-value");
      }
      return out;
    }
  }
}
