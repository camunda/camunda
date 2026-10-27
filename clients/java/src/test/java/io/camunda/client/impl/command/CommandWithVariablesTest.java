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
package io.camunda.client.impl.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandWithVariablesTest {

  private final TestCommand command = new TestCommand(new CamundaObjectMapper());

  @Test
  void shouldAccumulateVariablesAcrossAddMethods() {
    // given
    final Map<String, Object> additionalVariables = new HashMap<>();
    additionalVariables.put("second", 2);
    additionalVariables.put("third", 3);

    // when
    command.addVariable("first", 1).addVariables(additionalVariables);

    // then
    assertThat(command.getVariables())
        .containsOnly(entry("first", 1), entry("second", 2), entry("third", 3));
  }

  @Test
  void shouldReplaceExistingValueWhenAddingVariableWithSameName() {
    // when
    command.addVariable("variable", "old").addVariable("variable", "new");

    // then
    assertThat(command.getVariables()).containsOnly(entry("variable", "new"));
  }

  @Test
  void shouldPreserveDecimalPrecisionWhenAddingVariableAfterVariablesObject() {
    // given
    final String preciseValue = "0.123456789012345678901234567890";
    final PreciseVariablesDocument variables =
        new PreciseVariablesDocument(new BigDecimal(preciseValue));

    // when
    command.variables(variables).addVariable("added", true);

    // then
    assertThat(command.getSerializedVariables()).contains("\"amount\":" + preciseValue);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("replacingVariableSetters")
  void shouldReplaceAccumulatedVariables(
      final String description, final Consumer<TestCommand> replaceVariables) {
    // given
    command.addVariable("discarded", "value");

    // when
    replaceVariables.accept(command);
    command.addVariable("added", "value");

    // then
    assertThat(command.getVariables())
        .containsOnly(entry("replacement", "value"), entry("added", "value"));
  }

  @Test
  void shouldResetAccumulatedVariablesForEmptyJson() {
    // given
    command.addVariable("discarded", "value");

    // when
    command.variables("").addVariable("added", "value");

    // then
    assertThat(command.getVariables()).containsOnly(entry("added", "value"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullVariableSetters")
  void shouldForwardJsonNullAndResetAccumulatedVariables(
      final String description, final Consumer<TestCommand> setVariables) {
    // given
    command.addVariable("discarded", "value");

    // when
    setVariables.accept(command);

    // then
    assertThat(command.getSerializedVariables()).isEqualTo("null");

    command.addVariable("added", "value");
    assertThat(command.getVariables()).containsOnly(entry("added", "value"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonObjectVariableSetters")
  void shouldRejectVariablesThatAreNotAJsonObjectWithoutChangingAccumulatedVariables(
      final String description,
      final Consumer<TestCommand> setVariables,
      final String expectedValue) {
    // given
    command.addVariable("existing", "value");

    // when / then
    assertThatThrownBy(() -> setVariables.accept(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("variables must be a JSON object, but was: " + expectedValue);

    command.addVariable("added", "value");
    assertThat(command.getVariables())
        .containsOnly(entry("existing", "value"), entry("added", "value"));
  }

  @Test
  void shouldTruncateRejectedJsonValueInErrorMessage() {
    // given
    final String largeValue = String.join("", Collections.nCopies(600, "a"));

    // when / then
    assertThatThrownBy(() -> command.variables("[\"" + largeValue + "\"]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("variables must be a JSON object, but was: [")
        .hasMessageEndingWith("...")
        .hasMessageNotContaining(largeValue);
  }

  private static Stream<Arguments> nonObjectVariableSetters() {
    return Stream.of(
        Arguments.of(
            "JSON string",
            (Consumer<TestCommand>) command -> command.variables("[1,2,3]"),
            "[1, 2, 3]"),
        Arguments.of(
            "JSON stream",
            (Consumer<TestCommand>)
                command ->
                    command.variables(
                        new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8))),
            "[1, 2, 3]"),
        Arguments.of(
            "object",
            (Consumer<TestCommand>) command -> command.variables(Arrays.asList(1, 2, 3)),
            "[1, 2, 3]"));
  }

  private static Stream<Arguments> nullVariableSetters() {
    return Stream.of(
        Arguments.of("JSON string", (Consumer<TestCommand>) command -> command.variables("null")),
        Arguments.of(
            "JSON stream",
            (Consumer<TestCommand>)
                command ->
                    command.variables(
                        new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8)))));
  }

  private static Stream<Arguments> replacingVariableSetters() {
    return Stream.of(
        Arguments.of(
            "single variable",
            (Consumer<TestCommand>) command -> command.variable("replacement", "value")),
        Arguments.of(
            "variables map",
            (Consumer<TestCommand>)
                command -> command.variables(Collections.singletonMap("replacement", "value"))),
        Arguments.of(
            "variables JSON",
            (Consumer<TestCommand>) command -> command.variables("{\"replacement\":\"value\"}")),
        Arguments.of(
            "variables stream",
            (Consumer<TestCommand>)
                command ->
                    command.variables(
                        new ByteArrayInputStream(
                            "{\"replacement\":\"value\"}".getBytes(StandardCharsets.UTF_8)))),
        Arguments.of(
            "variables object",
            (Consumer<TestCommand>) command -> command.variables(new VariablesDocument())));
  }

  private static final class TestCommand extends CommandWithVariables<TestCommand> {

    private String serializedVariables;

    private TestCommand(final JsonMapper jsonMapper) {
      super(jsonMapper);
    }

    @Override
    protected TestCommand setVariablesInternal(final String variables) {
      serializedVariables = variables;
      return this;
    }

    private Map<String, Object> getVariables() {
      return objectMapper.fromJsonAsMap(serializedVariables);
    }

    private String getSerializedVariables() {
      return serializedVariables;
    }
  }

  private static final class VariablesDocument {

    public String getReplacement() {
      return "value";
    }
  }

  private static final class PreciseVariablesDocument {

    private final BigDecimal amount;

    private PreciseVariablesDocument(final BigDecimal amount) {
      this.amount = amount;
    }

    public BigDecimal getAmount() {
      return amount;
    }
  }
}
