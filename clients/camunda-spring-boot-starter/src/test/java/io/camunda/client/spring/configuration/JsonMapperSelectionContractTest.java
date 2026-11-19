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

import static io.camunda.client.spring.configuration.JsonMapperTestSupport.javaTimeVariables;
import static io.camunda.client.spring.configuration.JsonMapperTestSupport.plainVariables;
import static io.camunda.client.spring.configuration.JsonMapperTestSupport.selectedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.spring.configuration.JsonMapperTestSupport.JsonMapperSetup;
import io.camunda.client.spring.configuration.JsonMapperTestSupport.Order;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins which {@link JsonMapper} an application gets, and how it behaves, for each classpath and
 * bean setup in {@link JsonMapperSetup}, by observing the produced JSON rather than the type of the
 * mapper. See {@link JsonMapperTestSupport} for why and how the setups are booted. The exact output
 * is pinned in {@link JsonMapperGoldenOutputTest}.
 *
 * <p>Setups that use the Jackson 2 {@code ObjectMapper} of Spring Boot are expected to wrap a copy
 * of it, so Spring's Jackson settings apply to the variables sent by the client. This is how 8.8
 * behaved. Applications with Jackson 3 only (the Spring Boot 4 default) are deliberately not
 * switched: they keep the default mapper, which does not know {@code java.time} and ignores
 * Spring's Jackson settings. If that is changed on purpose, update {@link
 * #SETUPS_WITHOUT_SPRING_OBJECT_MAPPER} and the tests that refer to it.
 */
class JsonMapperSelectionContractTest {

  /**
   * Setups where no Jackson 2 {@code ObjectMapper} is registered when the starter is configured and
   * the client therefore falls back to the default mapper.
   */
  private static final Set<JsonMapperSetup> SETUPS_WITHOUT_SPRING_OBJECT_MAPPER =
      EnumSet.of(
          JsonMapperSetup.NO_SPRING_JACKSON,
          JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY,
          JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY_CONFIGURED);

  private static final Set<JsonMapperSetup> SETUPS_WITH_SPRING_SETTINGS =
      EnumSet.of(
          JsonMapperSetup.SPRING_JACKSON_2_ONLY_CONFIGURED,
          JsonMapperSetup.SPRING_BOOT_4_JACKSON_2_AND_3_CONFIGURED);

  private final ObjectMapper reader = new ObjectMapper();

  static Stream<Arguments> expectedSelection() {
    return Stream.of(
        Arguments.of(JsonMapperSetup.SPRING_JACKSON_2_ONLY, "JsonMapperConfiguration"),
        Arguments.of(JsonMapperSetup.SPRING_JACKSON_2_ONLY_CONFIGURED, "JsonMapperConfiguration"),
        Arguments.of(JsonMapperSetup.SPRING_BOOT_4_JACKSON_2_AND_3, "JsonMapperConfiguration"),
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_2_AND_3_CONFIGURED, "JsonMapperConfiguration"),
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY, "DefaultJsonMapperConfiguration"),
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY_CONFIGURED,
            "DefaultJsonMapperConfiguration"),
        Arguments.of(JsonMapperSetup.NO_SPRING_JACKSON, "DefaultJsonMapperConfiguration"),
        Arguments.of(JsonMapperSetup.USER_OBJECT_MAPPER, "JsonMapperConfiguration"),
        Arguments.of(
            JsonMapperSetup.USER_OBJECT_MAPPER_SPRING_BOOT_4_JACKSON_3_ONLY,
            "JsonMapperConfiguration"),
        Arguments.of(JsonMapperSetup.USER_JSON_MAPPER, "UserJsonMapperConfiguration"));
  }

  @ParameterizedTest
  @MethodSource("expectedSelection")
  void shouldSelectJsonMapperConfiguration(
      final JsonMapperSetup setup, final String expectedConfiguration) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(JsonMapper.class);
              assertThat(context.getBean(JsonMapper.class)).isInstanceOf(CamundaObjectMapper.class);
              assertThat(selectedBy(context)).isEqualTo(expectedConfiguration);
            });
  }

  @ParameterizedTest
  @EnumSource(JsonMapperSetup.class)
  void shouldSerializeCamundaTypesAndEmptyBeansInEverySetup(final JsonMapperSetup setup) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final JsonMapper jsonMapper = context.getBean(JsonMapper.class);

              // when
              final JsonNode json = reader.readTree(jsonMapper.toJson(plainVariables()));

              // then
              assertThat(json.get("filter").asText())
                  .as("an exact match filter is sent as a bare string")
                  .isEqualTo("admin");
              assertThat(json.get("empty").isObject()).isTrue();
            });
  }

  @ParameterizedTest
  @EnumSource(JsonMapperSetup.class)
  void shouldReadUnknownPropertiesAndRoundTripInEverySetup(final JsonMapperSetup setup) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final JsonMapper jsonMapper = context.getBean(JsonMapper.class);
              final Order order = new Order("A-1", 3, "note");

              // when
              final Order roundTripped = jsonMapper.fromJson(jsonMapper.toJson(order), Order.class);
              final Order withUnknownProperty =
                  jsonMapper.fromJson(
                      jsonMapper.toJson(order).replaceFirst("\\{", "{\"unknown\":true,"),
                      Order.class);

              // then
              assertThat(roundTripped).isEqualTo(order);
              assertThat(withUnknownProperty).isEqualTo(order);
            });
  }

  @ParameterizedTest
  @EnumSource(JsonMapperSetup.class)
  void shouldSerializeJavaTimeTypesAsIso8601StringsIfTheSetupSupportsThem(
      final JsonMapperSetup setup) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final JsonMapper jsonMapper = context.getBean(JsonMapper.class);

              if (SETUPS_WITHOUT_SPRING_OBJECT_MAPPER.contains(setup)
                  || setup == JsonMapperSetup.USER_JSON_MAPPER) {
                // the default mapper has no java.time support; the user mapper is not ours to judge
                assertThatThrownBy(() -> jsonMapper.toJson(javaTimeVariables()))
                    .isInstanceOf(InternalClientException.class)
                    .hasMessageContaining("Failed to serialize object");
                return;
              }

              // when
              final JsonNode json = reader.readTree(jsonMapper.toJson(javaTimeVariables()));

              // then
              assertThat(json.get("date").asText()).isEqualTo("1990-01-01");
              assertThat(json.get("duration").asText()).isEqualTo("PT1H30M");
              assertThat(json.get("instant").asText()).isEqualTo("2024-02-29T10:15:30Z");
            });
  }

  @ParameterizedTest
  @EnumSource(JsonMapperSetup.class)
  void shouldApplySpringJacksonSettingsOnlyWhenSpringObjectMapperIsWrapped(
      final JsonMapperSetup setup) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final JsonMapper jsonMapper = context.getBean(JsonMapper.class);

              // when
              final JsonNode order =
                  reader.readTree(jsonMapper.toJson(plainVariables())).get("order");

              // then
              if (SETUPS_WITH_SPRING_SETTINGS.contains(setup)) {
                assertThat(order.has("order_id"))
                    .as("spring.jackson*.property-naming-strategy applies")
                    .isTrue();
                assertThat(order.has("note"))
                    .as("spring.jackson*.default-property-inclusion applies")
                    .isFalse();
              } else if (setup == JsonMapperSetup.USER_OBJECT_MAPPER
                  || setup == JsonMapperSetup.USER_OBJECT_MAPPER_SPRING_BOOT_4_JACKSON_3_ONLY) {
                assertThat(order.has("order-id"))
                    .as("the settings of the user's ObjectMapper apply, not those of Spring")
                    .isTrue();
                assertThat(order.has("note")).isTrue();
              } else if (setup == JsonMapperSetup.USER_JSON_MAPPER) {
                assertThat(order.has("OrderId")).as("the user's JsonMapper is used").isTrue();
              } else {
                assertThat(order.has("orderId"))
                    .as("Jackson defaults, Spring Jackson settings are not applied")
                    .isTrue();
                assertThat(order.has("note")).isTrue();
              }
            });
  }

  @ParameterizedTest
  @EnumSource(
      value = JsonMapperSetup.class,
      names = {
        "SPRING_JACKSON_2_ONLY",
        "SPRING_BOOT_4_JACKSON_2_AND_3",
        "USER_OBJECT_MAPPER",
        "USER_OBJECT_MAPPER_SPRING_BOOT_4_JACKSON_3_ONLY"
      })
  void shouldNotChangeTheObjectMapperOfTheApplication(final JsonMapperSetup setup) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final ObjectMapper applicationObjectMapper = context.getBean(ObjectMapper.class);

              // when
              final String filterJson =
                  applicationObjectMapper.writeValueAsString(
                      JsonMapperTestSupport.exactMatchFilter());

              // then
              assertThat(
                      applicationObjectMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS))
                  .as("the client works on a copy, the application's mapper stays as configured")
                  .isTrue();
              assertThat(filterJson)
                  .as("the application's mapper does not get the Camunda modules")
                  .startsWith("{")
                  .contains("\"$eq\":\"admin\"");
            });
  }

  @Test
  void shouldSerializeOptionalsWhenJdk8ModuleIsAvailable() {
    assumeTrue(
        JsonMapperTestSupport.isPresent("com.fasterxml.jackson.datatype.jdk8.Jdk8Module"),
        "jackson-datatype-jdk8 is not on the classpath");
    final JsonMapperSetup setup = JsonMapperSetup.SPRING_JACKSON_2_ONLY;
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // when
              final String json = context.getBean(JsonMapper.class).toJson(Optional.of("present"));

              // then
              assertThat(json).isEqualTo("\"present\"");
            });
  }
}
