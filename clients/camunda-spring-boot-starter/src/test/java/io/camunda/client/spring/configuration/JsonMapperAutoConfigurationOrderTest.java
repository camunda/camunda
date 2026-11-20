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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.spring.configuration.JsonMapperTestSupport.AutoConfigurationApplication;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies that the {@link JsonMapper} is created from the {@code ObjectMapper} configured by
 * Spring Boot's Jackson 2 auto-configuration when the starter is registered through the regular
 * auto-configuration mechanism, as in an application. This requires {@link
 * CamundaAutoConfiguration} to be ordered after that auto-configuration. Otherwise the
 * {@code @ConditionalOnBean} condition of {@link JsonMapperConfiguration} is evaluated before the
 * Spring {@code ObjectMapper} is registered, and the client silently falls back to a plain mapper
 * that, among others, cannot serialize {@code java.time} types.
 *
 * <p>The ordering annotations on {@link JsonMapperConfiguration} and the other JSON mapper
 * configurations have no effect, because they are imported by {@link CamundaAutoConfiguration} and
 * not registered as auto-configurations themselves. The full auto-configuration candidate list is
 * used on purpose: listing the auto-configurations with {@code AutoConfigurations.of} does not
 * reproduce the ordering, as the nested imports are then not processed in the auto-configuration
 * phase.
 *
 * <p>The test runs in the Spring Boot 4 starter (Jackson 2 and 3 auto-configurations) and in the
 * Spring Boot 3 starter, which uses the same {@link CamundaAutoConfiguration} but lists it in its
 * own {@code .imports} file, so it must not reference version specific classes. The setups that
 * differ in Jackson versions are covered by {@link JsonMapperSelectionContractTest}.
 */
class JsonMapperAutoConfigurationOrderTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AutoConfigurationApplication.class);

  @Test
  void shouldCreateJsonMapperFromSpringObjectMapper() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(JsonMapper.class);
          assertThat(context.getBeanFactory().getBeanDefinition("camundaJsonMapper"))
              .as("the JsonMapper must be created from the Spring ObjectMapper, not the fallback")
              .extracting(definition -> definition.getFactoryBeanName())
              .isEqualTo(JsonMapperConfiguration.class.getName());
        });
  }

  @Test
  void shouldSerializeJavaTimeTypesWithSpringObjectMapper() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          final JsonMapper jsonMapper = context.getBean(JsonMapper.class);

          final String json = jsonMapper.toJson(Map.of("date", LocalDate.of(1990, 1, 1)));

          assertThat(json).isEqualTo("{\"date\":\"1990-01-01\"}");
        });
  }
}
