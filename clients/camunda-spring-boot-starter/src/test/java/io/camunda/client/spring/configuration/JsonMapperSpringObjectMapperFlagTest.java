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
import io.camunda.client.impl.CamundaObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers the opt-in {@code camunda.client.use-spring-object-mapper} property. Goes through
 * {@code @EnableAutoConfiguration}, the same path a user application takes, because the default
 * mapper selection depends on the auto-configuration order.
 */
@SpringBootTest(classes = JsonMapperSpringObjectMapperFlagTest.AutoConfiguredApp.class)
class JsonMapperSpringObjectMapperFlagTest {

  private static final String ENABLED = "camunda.client.use-spring-object-mapper=true";
  // Spring Boot 4 (Jackson 2 support) and Spring Boot 3; names missing from the classpath are
  // ignored
  private static final String WITHOUT_SPRING_OBJECT_MAPPER =
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
  private static final long EPOCH_MILLIS = Instant.parse("2026-11-19T00:00:00Z").toEpochMilli();
  private static final JsonMapper OWN_JSON_MAPPER = new CamundaObjectMapper();

  @Nested
  class WhenNotSet {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldKeepDefaultMapperOutput() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  @Nested
  @TestPropertySource(properties = ENABLED)
  class WhenEnabled {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldSerializeJavaTimeWithSpringObjectMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", LocalDate.of(1990, 1, 1));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":\"1990-01-01\"}");
    }

    @Test
    void shouldSerializeUtilDateWithSpringObjectMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":\"2026-11-19T00:00:00.000+00:00\"}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        ENABLED,
        // Spring Boot 3 reads spring.jackson.*, Spring Boot 4's Jackson 2 support spring.jackson2.*
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "spring.jackson2.property-naming-strategy=SNAKE_CASE"
      })
  class WhenEnabledWithSpringJacksonProperties {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldApplySpringJacksonProperties() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(properties = {ENABLED, "test.own-json-mapper=true"})
  class WhenEnabledWithOwnJsonMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseOwnJsonMapper() {
      // when / then
      assertThat(jsonMapper).isSameAs(OWN_JSON_MAPPER);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {ENABLED, WITHOUT_SPRING_OBJECT_MAPPER, "test.library-object-mapper=true"})
  class WhenEnabledWithObjectMapperFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseThatObjectMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(properties = {ENABLED, WITHOUT_SPRING_OBJECT_MAPPER})
  class WhenEnabledWithoutSpringObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldFallBackToDefaultMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  public record Order(String orderId) {}

  @Configuration
  @EnableAutoConfiguration
  static class AutoConfiguredApp {

    @Bean
    @ConditionalOnProperty("test.own-json-mapper")
    JsonMapper ownJsonMapper() {
      return OWN_JSON_MAPPER;
    }
  }
}
