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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers which JSON mapper the starter uses for job variables. Goes through
 * {@code @EnableAutoConfiguration}, the same path a user application takes, because the mapper
 * selection depends on the auto-configuration order; listing the configuration classes directly
 * would skip it.
 *
 * <p>Tests marked "8.9.0 to the fix" state the output of the releases that ignored Spring's Jackson
 * 2 ObjectMapper, so the behavior change of the fix is visible.
 */
@SpringBootTest(classes = JsonMapperAutoConfigurationTest.AutoConfiguredApp.class)
class JsonMapperAutoConfigurationTest {

  // Spring Boot 4 (Jackson 2 support and Jackson 3) and Spring Boot 3; names missing from the
  // classpath are ignored
  private static final String WITHOUT_SPRING_OBJECT_MAPPER =
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration,"
          + "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
  private static final long EPOCH_MILLIS = Instant.parse("2026-11-19T00:00:00Z").toEpochMilli();
  private static final JsonMapper OWN_JSON_MAPPER = new CamundaObjectMapper();

  @Autowired JsonMapper jsonMapper;

  @Test
  void shouldSerializeJavaTimeWithSpringObjectMapper() {
    // given
    final Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("localDate", LocalDate.of(1990, 1, 1));
    variables.put("localDateTime", LocalDateTime.of(1990, 1, 1, 10, 15, 30));
    variables.put("instant", Instant.parse("2024-02-29T10:15:30Z"));
    variables.put(
        "offsetDateTime", OffsetDateTime.of(2024, 2, 29, 10, 15, 30, 0, ZoneOffset.ofHours(2)));
    variables.put("duration", Duration.ofMinutes(90));

    // when
    final String json = jsonMapper.toJson(variables);

    // then
    // 8.9.0 to the fix: InvalidDefinitionException for every java.time type
    assertThat(json)
        .isEqualTo(
            "{\"localDate\":\"1990-01-01\","
                + "\"localDateTime\":\"1990-01-01T10:15:30\","
                + "\"instant\":\"2024-02-29T10:15:30Z\","
                + "\"offsetDateTime\":\"2024-02-29T10:15:30+02:00\","
                + "\"duration\":\"PT1H30M\"}");
  }

  @Test
  void shouldDeserializeJavaTimeWithSpringObjectMapper() {
    // given
    final String json = "{\"date\":\"1990-01-01\"}";

    // when
    final DateHolder holder = jsonMapper.fromJson(json, DateHolder.class);

    // then
    // 8.9.0 to the fix: InvalidDefinitionException
    assertThat(holder.date).isEqualTo(LocalDate.of(1990, 1, 1));
  }

  @Test
  void shouldSerializeUtilDateAsIsoString() {
    // given
    final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

    // when
    final String json = jsonMapper.toJson(variables);

    // then
    // 8.9.0 to the fix: {"date":1795046400000}
    assertThat(json).isEqualTo("{\"date\":\"2026-11-19T00:00:00.000+00:00\"}");
  }

  static boolean isSpringBoot3() {
    return SpringBootVersion.getVersion().startsWith("3.");
  }

  @Nested
  @TestPropertySource(
      properties = {
        // Spring Boot 3 reads spring.jackson.*, Spring Boot 4's Jackson 2 support spring.jackson2.*
        "spring.jackson.property-naming-strategy=SNAKE_CASE",
        "spring.jackson.default-property-inclusion=non_null",
        "spring.jackson2.property-naming-strategy=SNAKE_CASE",
        "spring.jackson2.default-property-inclusion=non_null"
      })
  class WithSpringJacksonProperties {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldApplyPropertyNamingStrategy() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      // 8.9.0 to the fix: {"orderId":"A-1"}
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }

    @Test
    void shouldApplyPropertyInclusion() {
      // given
      final NullableHolder holder = new NullableHolder();

      // when
      final String json = jsonMapper.toJson(holder);

      // then
      // 8.9.0 to the fix: {"present":"x","absent":null}
      assertThat(json).isEqualTo("{\"present\":\"x\"}");
    }

    @Test
    void shouldReadVariablesWrittenWithoutTheNamingStrategyAsNull() {
      // given
      final String json = "{\"orderId\":\"A-1\"}";

      // when
      final Order order = jsonMapper.fromJson(json, Order.class);

      // then
      // 8.9.0 to the fix: orderId is "A-1"
      assertThat(order.orderId()).isNull();
    }
  }

  // On 8.9 the Spring Boot 3 starter orders its auto-configuration after Spring Boot's
  // JacksonAutoConfiguration, so it always used that ObjectMapper and the opt-out keeps it.
  @Nested
  @DisabledIf(
      value =
          "io.camunda.client.spring.configuration.JsonMapperAutoConfigurationTest#isSpringBoot3",
      disabledReason = "The Spring Boot 3 starter of 8.9 never used the default mapper here")
  @TestPropertySource(properties = "camunda.client.use-bare-object-mapper=true")
  class WithBareObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.client.use-bare-object-mapper=true", "test.own-object-mapper=true"})
  class WithBareObjectMapperAndOwnObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseOwnObjectMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(properties = "test.own-json-mapper=true")
  class WithOwnJsonMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseOwnJsonMapper() {
      // when / then
      assertThat(jsonMapper).isSameAs(OWN_JSON_MAPPER);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {WITHOUT_SPRING_OBJECT_MAPPER, "test.library-object-mapper=true"})
  class WithObjectMapperFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldUseThatObjectMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }

    @Test
    void shouldNotChangeThatObjectMapper() {
      // when / then
      // CamundaObjectMapper disables FAIL_ON_EMPTY_BEANS on the mapper it wraps
      assertThat(objectMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // Spring Boot 3's Jackson 2 message converter fails on several non-primary ObjectMappers
        WITHOUT_SPRING_OBJECT_MAPPER
            + ",org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration",
        "test.library-object-mapper=true",
        "test.second-library-object-mapper=true"
      })
  class WithSeveralObjectMappersFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"orderId\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        WITHOUT_SPRING_OBJECT_MAPPER,
        "test.library-object-mapper=true",
        "test.second-library-object-mapper=true",
        "test.primary-library-object-mapper=true"
      })
  class WithPrimaryObjectMapperFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUsePrimaryObjectMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"order-id\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // Spring Boot 3's Jackson 2 message converter fails on several primary ObjectMappers
        WITHOUT_SPRING_OBJECT_MAPPER
            + ",org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration",
        "test.primary-library-object-mapper=true",
        "test.second-primary-library-object-mapper=true"
      })
  class WithSeveralPrimaryObjectMappersFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"orderId\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {WITHOUT_SPRING_OBJECT_MAPPER, "test.library-non-json-mapper=true"})
  class WithNonJsonMapperFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Order order = new Order("A-1");

      // when
      final String json = jsonMapper.toJson(order);

      // then
      assertThat(json).isEqualTo("{\"orderId\":\"A-1\"}");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {WITHOUT_SPRING_OBJECT_MAPPER, "test.library-non-copyable-object-mapper=true"})
  class WithNonCopyableObjectMapperFromLaterAutoConfiguration {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  @Nested
  @TestPropertySource(properties = WITHOUT_SPRING_OBJECT_MAPPER)
  class WithoutSpringObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  public static class DateHolder {
    public LocalDate date;
  }

  public static class NullableHolder {
    public String present = "x";
    public String absent;
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

    @Bean
    @ConditionalOnProperty("test.own-object-mapper")
    ObjectMapper ownObjectMapper() {
      return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
  }
}
