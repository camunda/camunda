/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.validator.AgentInstanceRequestValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code historyItemId} and {@code loopIteration}'s requiredness on {@code
 * AgentInstanceHistoryItem} in {@code agent-instances.yaml} is documentation only: like every other
 * constraint declared there, the generated request model's {@code @NotNull} is not enforced during
 * deserialization (see the comment on {@link AgentInstanceRequestValidator#validateUpdateRequest}).
 * {@link AgentInstanceRequestValidator}'s manual null/blank check is the only real enforcement.
 * This test guards the spec side of that pair: if a future edit removes one of these fields from
 * {@code AgentInstanceHistoryItem}'s {@code required} list, the published contract would stop
 * matching a validator that still rejects the request. The validator side needs no guard here --
 * {@code AgentInstanceRequestValidatorTest} already fails if either rule is relaxed.
 *
 * <p>Reads {@code agent-instances.yaml} as plain text (bundled onto this module's classpath by the
 * {@code zeebe-gateway-protocol} dependency), the same approach as {@link
 * SecretRequestValidatorSpecSyncTest}, and for the same reason: the file is referenced piecemeal by
 * {@code rest-api.yaml}, so a full-spec parse does not reliably resolve its schemas.
 */
class AgentInstanceRequestValidatorSpecSyncTest {

  private static final String AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE = "v2/agent-instances.yaml";
  private static final String SCHEMA_UNDER_TEST = "AgentInstanceHistoryItem";

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"historyItemId", "loopIteration"})
  void shouldDeclareHistoryItemFieldRequiredInSpec(final String fieldUnderTest) {
    // given the required: list declared on the AgentInstanceHistoryItem schema in
    // agent-instances.yaml
    final var declaredRequired = requiredFieldsOf(schemaUnderTest());

    // then
    assertThat(declaredRequired)
        .as(
            ("Expected the '%s' schema in '%s' to declare '%s' as required, matching the rule"
                    + " AgentInstanceRequestValidator enforces.")
                .formatted(
                    SCHEMA_UNDER_TEST, AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE, fieldUnderTest))
        .contains(fieldUnderTest);
  }

  private static List<String> requiredFieldsOf(final String schemaBlock) {
    final var requiredBlock =
        Pattern.compile("required:\\n((?:\\s+- \\w+\\n)+)").matcher(schemaBlock);
    if (!requiredBlock.find()) {
      throw new AssertionError(
          "Expected the '%s' schema in '%s' to declare a 'required' list, but none was found."
              .formatted(SCHEMA_UNDER_TEST, AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE));
    }
    final var itemPattern = Pattern.compile("- (\\w+)");
    final Matcher itemMatcher = itemPattern.matcher(requiredBlock.group(1));
    final var fields = new java.util.ArrayList<String>();
    while (itemMatcher.find()) {
      fields.add(itemMatcher.group(1));
    }
    return fields;
  }

  /**
   * Slices {@code agent-instances.yaml} down to the {@code AgentInstanceHistoryItem} schema block:
   * from its declaration to the next top-level ({@code schemaName:}, 4-space indented) schema key.
   */
  private static String schemaUnderTest() {
    final var yaml = agentInstancesYaml();
    // exact match on "AgentInstanceHistoryItem:" -- the colon excludes similarly-prefixed
    // sibling schemas such as AgentInstanceHistoryItemResult.
    final var start = yaml.indexOf(SCHEMA_UNDER_TEST + ":");
    if (start < 0) {
      throw new AssertionError(
          "Expected '%s' to declare a '%s' schema, but none was found."
              .formatted(AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE, SCHEMA_UNDER_TEST));
    }
    final var nextSchema =
        Pattern.compile("\\n {4}\\w+:")
            .matcher(yaml)
            .region(start + SCHEMA_UNDER_TEST.length(), yaml.length());
    final var end = nextSchema.find() ? nextSchema.start() : yaml.length();
    return yaml.substring(start, end);
  }

  private static String agentInstancesYaml() {
    try (var in =
        AgentInstanceRequestValidatorSpecSyncTest.class
            .getClassLoader()
            .getResourceAsStream(AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE)) {
      if (in == null) {
        throw new AssertionError(
            "Classpath resource not found: " + AGENT_INSTANCES_YAML_CLASSPATH_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
