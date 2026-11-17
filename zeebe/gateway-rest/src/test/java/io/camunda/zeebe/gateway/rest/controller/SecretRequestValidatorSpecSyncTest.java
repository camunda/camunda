/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.validator.SecretRequestValidator;
import io.camunda.service.SecretServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link SecretRequestValidator#MAX_BATCH_SIZE} and {@link SecretServices#MAX_REFERENCE_LENGTH} are
 * the only real enforcement of the {@code maxItems}/{@code maxLength} declared on {@code
 * SecretResolveRequest.references} in {@code secrets.yaml}: the generated request model does not
 * emit array-size or length constraints, so nothing else fails the build if the two drift apart.
 * This test is that guard.
 *
 * <p>Reads {@code secrets.yaml} as plain text (it is bundled onto this module's classpath by the
 * {@code zeebe-gateway-protocol} dependency) rather than through an OpenAPI parser: the file is a
 * fragment referenced piecemeal by {@code rest-api.yaml} ($ref per path item, not per schema), so a
 * full-spec parse does not reliably resolve its schemas back onto a single root document.
 *
 * <p>Extraction is scoped to the {@code SecretResolveRequest} schema block (up to the next
 * top-level schema key), not the whole file: other schemas in {@code secrets.yaml} may later
 * declare their own {@code maxItems}/{@code maxLength} constraints (e.g. pagination on {@code
 * SecretListResult}), and this test must not break when they do.
 */
class SecretRequestValidatorSpecSyncTest {

  private static final String SECRETS_YAML_CLASSPATH_RESOURCE = "v2/secrets.yaml";
  private static final String SCHEMA_UNDER_TEST = "SecretResolveRequest";

  @Test
  void shouldMatchMaxBatchSizeDeclaredInSpec() {
    // given the maxItems declared on SecretResolveRequest.references in secrets.yaml
    final var maxItems = extractIntValue("maxItems");

    // then the validator's cap matches it exactly
    assertThat(maxItems).isEqualTo(SecretRequestValidator.MAX_BATCH_SIZE);
  }

  @Test
  void shouldMatchMaxReferenceLengthDeclaredInSpec() {
    // given the maxLength declared on each references item in secrets.yaml
    final var maxLength = extractIntValue("maxLength");

    // then the service's cap matches it exactly
    assertThat(maxLength).isEqualTo(SecretServices.MAX_REFERENCE_LENGTH);
  }

  private static int extractIntValue(final String yamlKey) {
    final var pattern = Pattern.compile(yamlKey + ":\\s*(\\d+)");
    final Matcher matcher = pattern.matcher(schemaUnderTest());
    if (!matcher.find()) {
      throw new AssertionError(
          "Expected the '%s' schema in '%s' to declare a '%s' constraint, but none was found."
              .formatted(SCHEMA_UNDER_TEST, SECRETS_YAML_CLASSPATH_RESOURCE, yamlKey));
    }
    final var value = Integer.parseInt(matcher.group(1));
    if (matcher.find()) {
      throw new AssertionError(
          "Expected the '%s' schema in '%s' to declare exactly one '%s' constraint, but found "
              + "more than one. "
                  .formatted(SCHEMA_UNDER_TEST, SECRETS_YAML_CLASSPATH_RESOURCE, yamlKey)
              + "Narrow this test's extraction so it targets the right occurrence.");
    }
    return value;
  }

  /**
   * Slices {@code secrets.yaml} down to the {@code SecretResolveRequest} schema block: from its
   * declaration to the next top-level ({@code schemaName:}, 4-space indented) schema key.
   */
  private static String schemaUnderTest() {
    final var yaml = secretsYaml();
    final var start = yaml.indexOf(SCHEMA_UNDER_TEST + ":");
    if (start < 0) {
      throw new AssertionError(
          "Expected '%s' to declare a '%s' schema, but none was found."
              .formatted(SECRETS_YAML_CLASSPATH_RESOURCE, SCHEMA_UNDER_TEST));
    }
    final var nextSchema =
        Pattern.compile("\\n {4}\\w+:")
            .matcher(yaml)
            .region(start + SCHEMA_UNDER_TEST.length(), yaml.length());
    final var end = nextSchema.find() ? nextSchema.start() : yaml.length();
    return yaml.substring(start, end);
  }

  private static String secretsYaml() {
    try (var in =
        SecretRequestValidatorSpecSyncTest.class
            .getClassLoader()
            .getResourceAsStream(SECRETS_YAML_CLASSPATH_RESOURCE)) {
      if (in == null) {
        throw new AssertionError(
            "Classpath resource not found: " + SECRETS_YAML_CLASSPATH_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
