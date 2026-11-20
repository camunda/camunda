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
import static io.camunda.client.spring.configuration.JsonMapperTestSupport.serializeOrDescribeFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.spring.configuration.JsonMapperTestSupport.JsonMapperSetup;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins, byte for byte, the JSON the client sends for a fixed set of variables in each setup of
 * {@link JsonMapperSetup}. It guards what applications observe across upgrades and when changing
 * the mapper selection: the wire format of the variables must only change on purpose.
 *
 * <p>If this test fails after a change, do not just paste the new output. Decide whether the
 * applications in the affected setup should see it, and say so in the change description. To update
 * a value deliberately, run this test, replace the expected string of the setup in {@link
 * #expectedOutputs()} with the {@code but was} value of the failure, and review the diff of the
 * expected strings as the list of behavior changes.
 *
 * <p>A value starting with {@code ERROR} is the exception chain of a payload that the mapper of the
 * setup cannot serialize. It is pinned as well, as it is the behavior applications have today. The
 * default mapper, which is used without a Jackson 2 {@code ObjectMapper} in the context, is the
 * case where this is currently so for {@code java.time} types.
 *
 * <p>Naming: <i>spring</i> is the output of a copy of Spring Boot's Jackson 2 mapper (Boot
 * defaults: ISO-8601 dates, nulls written), <i>configured</i> adds {@code
 * spring.jackson.property-naming-strategy=SNAKE_CASE} and {@code
 * spring.jackson.default-property-inclusion=non_null} (and the {@code spring.jackson2.*}
 * equivalents), <i>default</i> is the mapper the client creates itself.
 */
class JsonMapperGoldenOutputTest {

  private static final String DEFAULT_PLAIN =
      "{\"order\":{\"orderId\":\"A-1\",\"itemCount\":3,\"note\":null},\"empty\":{},\"filter\":\"admin\"}";
  private static final String DEFAULT_JAVA_TIME =
      "ERROR InternalClientException <- InvalidDefinitionException";

  private static final String SPRING_PLAIN = DEFAULT_PLAIN;
  private static final String SPRING_JAVA_TIME =
      "{\"date\":\"1990-01-01\",\"duration\":\"PT1H30M\",\"instant\":\"2024-02-29T10:15:30Z\"}";

  private static final String SPRING_CONFIGURED_PLAIN =
      "{\"order\":{\"order_id\":\"A-1\",\"item_count\":3},\"empty\":{},\"filter\":\"admin\"}";

  private static final String USER_OBJECT_MAPPER_PLAIN =
      "{\"order\":{\"order-id\":\"A-1\",\"item-count\":3,\"note\":null},\"empty\":{},\"filter\":\"admin\"}";

  private static final String USER_JSON_MAPPER_PLAIN =
      "{\"order\":{\"OrderId\":\"A-1\",\"ItemCount\":3,\"Note\":null},\"empty\":{},\"filter\":\"admin\"}";

  /**
   * Arguments: setup, expected output of the plain variables, expected output of java.time ones.
   */
  static Stream<Arguments> expectedOutputs() {
    return Stream.of(
        // Spring Boot's Jackson 2 mapper only (Spring Boot 3, or Spring Boot 4 +
        // spring-boot-jackson2)
        Arguments.of(JsonMapperSetup.SPRING_JACKSON_2_ONLY, SPRING_PLAIN, SPRING_JAVA_TIME),
        Arguments.of(
            JsonMapperSetup.SPRING_JACKSON_2_ONLY_CONFIGURED,
            SPRING_CONFIGURED_PLAIN,
            SPRING_JAVA_TIME),
        // Spring Boot 4 with Jackson 3 and the Jackson 2 compatibility auto-configuration
        Arguments.of(JsonMapperSetup.SPRING_BOOT_4_JACKSON_2_AND_3, SPRING_PLAIN, SPRING_JAVA_TIME),
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_2_AND_3_CONFIGURED,
            SPRING_CONFIGURED_PLAIN,
            SPRING_JAVA_TIME),
        // Spring Boot 4 default, Jackson 3 only: the default mapper, Spring settings do not apply
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY, DEFAULT_PLAIN, DEFAULT_JAVA_TIME),
        Arguments.of(
            JsonMapperSetup.SPRING_BOOT_4_JACKSON_3_ONLY_CONFIGURED,
            DEFAULT_PLAIN,
            DEFAULT_JAVA_TIME),
        // no Jackson auto-configuration of Spring Boot at all
        Arguments.of(JsonMapperSetup.NO_SPRING_JACKSON, DEFAULT_PLAIN, DEFAULT_JAVA_TIME),
        // the application's own mappers
        Arguments.of(
            JsonMapperSetup.USER_OBJECT_MAPPER, USER_OBJECT_MAPPER_PLAIN, SPRING_JAVA_TIME),
        Arguments.of(
            JsonMapperSetup.USER_OBJECT_MAPPER_SPRING_BOOT_4_JACKSON_3_ONLY,
            USER_OBJECT_MAPPER_PLAIN,
            SPRING_JAVA_TIME),
        Arguments.of(JsonMapperSetup.USER_JSON_MAPPER, USER_JSON_MAPPER_PLAIN, DEFAULT_JAVA_TIME));
  }

  @ParameterizedTest
  @MethodSource("expectedOutputs")
  void shouldSerializeVariablesToTheExpectedJson(
      final JsonMapperSetup setup, final String expectedPlain, final String expectedJavaTime) {
    assumeTrue(setup.isAvailable());

    setup
        .runner()
        .run(
            context -> {
              // given
              final JsonMapper jsonMapper = context.getBean(JsonMapper.class);

              // when
              final String plain = serializeOrDescribeFailure(jsonMapper, plainVariables());
              final String javaTime = serializeOrDescribeFailure(jsonMapper, javaTimeVariables());

              // then
              assertThat(plain).as("plain variables in %s", setup).isEqualTo(expectedPlain);
              assertThat(javaTime)
                  .as("java.time variables in %s", setup)
                  .isEqualTo(expectedJavaTime);
            });
  }
}
