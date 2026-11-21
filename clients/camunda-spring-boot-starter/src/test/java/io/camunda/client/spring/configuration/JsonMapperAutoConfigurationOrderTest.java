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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * Reproduces SUPPORT-35485: with the real auto-configuration list, the starter must serialize
 * java.time values through Spring's ObjectMapper, not the bare fallback mapper.
 *
 * <p>Uses {@code @EnableAutoConfiguration} so CamundaAutoConfiguration is picked up from {@code
 * META-INF/spring/...AutoConfiguration.imports}, the same path a user application takes. Listing
 * the configuration classes directly would skip the ordering that breaks here.
 *
 * <p>The remaining tests pin what else changes for an application with Spring's Jackson 2
 * ObjectMapper once the starter uses it again. Each one states the output without the ordering fix
 * (the released 8.9.x behavior), so the behavior change is visible when deciding on a release note.
 */
@SpringBootTest(classes = JsonMapperAutoConfigurationOrderTest.AutoConfiguredApp.class)
class JsonMapperAutoConfigurationOrderTest {

  @Autowired JsonMapper jsonMapper;

  @Test
  void shouldSerializeJavaTimeWithSpringObjectMapper() {
    // given
    final Map<String, Object> variables = Map.of("date", LocalDate.of(1990, 1, 1));

    // when
    final String json = jsonMapper.toJson(variables);

    // then
    assertThat(json).contains("\"1990-01-01\"");
  }

  @Test
  void shouldSerializeOtherJavaTimeTypesAsIsoStrings() {
    // given
    final Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("localDateTime", LocalDateTime.of(1990, 1, 1, 10, 15, 30));
    variables.put("instant", Instant.parse("2024-02-29T10:15:30Z"));
    variables.put(
        "offsetDateTime", OffsetDateTime.of(2024, 2, 29, 10, 15, 30, 0, ZoneOffset.ofHours(2)));
    variables.put("duration", Duration.ofMinutes(90));

    // when
    final String json = jsonMapper.toJson(variables);

    // then
    // without the fix: InvalidDefinitionException for every java.time type
    assertThat(json)
        .isEqualTo(
            "{\"localDateTime\":\"1990-01-01T10:15:30\","
                + "\"instant\":\"2024-02-29T10:15:30Z\","
                + "\"offsetDateTime\":\"2024-02-29T10:15:30+02:00\","
                + "\"duration\":\"PT1H30M\"}");
  }

  @Test
  void shouldDeserializeJavaTimeValues() {
    // given
    final String json = "{\"date\":\"1990-01-01\"}";

    // when
    final DateHolder holder = jsonMapper.fromJson(json, DateHolder.class);

    // then
    // without the fix: InvalidDefinitionException
    assertThat(holder.date).isEqualTo(LocalDate.of(1990, 1, 1));
  }

  @Test
  void shouldSerializeUtilDateAsIsoString() {
    // given
    final Map<String, Object> variables = Map.of("date", new Date(0L));

    // when
    final String json = jsonMapper.toJson(variables);

    // then
    // without the fix: {"date":0}
    assertThat(json).isEqualTo("{\"date\":\"1970-01-01T00:00:00.000+00:00\"}");
  }

  @Test
  void shouldSerializeOptionalAsItsValue() {
    // given
    final OptionalHolder holder = new OptionalHolder();

    // when
    final String json = jsonMapper.toJson(holder);

    // then
    // without the fix: InvalidDefinitionException
    assertThat(json).isEqualTo("{\"value\":\"x\"}");
  }

  @Test
  void shouldDeserializeClassWithOnlyAPropertiesConstructor() {
    // given
    final String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\"}";

    // when
    final Person person = jsonMapper.fromJson(json, Person.class);

    // then
    // without the fix: InvalidDefinitionException, no creator found
    assertThat(person.getFirstName()).isEqualTo("John");
    assertThat(person.getLastName()).isEqualTo("Doe");
  }

  @Test
  void shouldNoLongerDeserializeSingleArgumentValueClassFromPlainString() {
    // given
    final String json = "\"A-1\"";

    // when
    final Throwable failure = catchThrowable(() -> jsonMapper.fromJson(json, OrderId.class));

    // then
    // without the fix: OrderId with value "A-1", the constructor is used as a delegating creator
    assertThat(failure).isInstanceOf(InternalClientException.class);
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
      // without the fix: {"orderId":"A-1"}
      assertThat(json).isEqualTo("{\"order_id\":\"A-1\"}");
    }

    @Test
    void shouldApplyPropertyInclusion() {
      // given
      final NullableHolder holder = new NullableHolder();

      // when
      final String json = jsonMapper.toJson(holder);

      // then
      // without the fix: {"present":"x","absent":null}
      assertThat(json).isEqualTo("{\"present\":\"x\"}");
    }

    @Test
    void shouldReadVariablesWrittenWithoutTheNamingStrategyAsNull() {
      // given
      final String json = "{\"orderId\":\"A-1\"}";

      // when
      final Order order = jsonMapper.fromJson(json, Order.class);

      // then
      // without the fix: orderId is "A-1"
      assertThat(order.orderId()).isNull();
    }
  }

  public static class DateHolder {
    public LocalDate date;
  }

  public static class OptionalHolder {
    public Optional<String> value = Optional.of("x");
  }

  public static class NullableHolder {
    public String present = "x";
    public String absent;
  }

  public record Order(String orderId) {}

  public static final class OrderId {
    private final String value;

    public OrderId(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static final class Person {
    private final String firstName;
    private final String lastName;

    public Person(final String firstName, final String lastName) {
      this.firstName = firstName;
      this.lastName = lastName;
    }

    public String getFirstName() {
      return firstName;
    }

    public String getLastName() {
      return lastName;
    }
  }

  @Configuration
  @EnableAutoConfiguration
  static class AutoConfiguredApp {}
}
