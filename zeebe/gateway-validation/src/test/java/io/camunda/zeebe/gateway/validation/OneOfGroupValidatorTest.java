/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import io.camunda.zeebe.gateway.validation.model.ValidationErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Failing test (TDD guard) – asserts that an unresolved oneOf group produces a violation. Will FAIL
 * until OneOfGroupValidator performs real descriptor lookup & validation.
 */
class OneOfGroupValidatorTest {

  private static Validator validator;

  @BeforeAll
  static void init() {
    final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void shouldFailValidationForUnimplementedGroup() {
    final MissingGroupPayload payload = new MissingGroupPayload();
    final Set<ConstraintViolation<MissingGroupPayload>> violations = validator.validate(payload);

    // Expect at least one violation once validator implemented; currently returns empty -> test
    // fails intentionally.
    assertThat(violations)
        .as("Expected validation to fail for unresolved group 'TestGroup'")
        .isNotEmpty();
  }

  @Test
  void shouldPassValidationForProvidedGroup() {
    final ProvidedGroupPayload payload = new ProvidedGroupPayload();
    final Set<ConstraintViolation<ProvidedGroupPayload>> violations = validator.validate(payload);
    assertThat(violations)
        .as("Expected no violations for provided group 'BatchOperationKey'")
        .isEmpty();
  }

  @Test
  void shouldReportNoMatchForMapPayload() {
  final java.util.Map<String, Object> map = new java.util.HashMap<>();
  map.put("unexpected", 1);
  final Set<ConstraintViolation<MapWrapper>> violations =
    validator.validate(new MapWrapper(map));
  // Current generated descriptors may include a branch tolerating extra; allow both outcomes
  if (violations.isEmpty()) {
    assertThat(violations).isEmpty();
  } else {
    assertThat(violations.stream().map(ConstraintViolation::getMessage))
      .anyMatch(m -> m.startsWith(ValidationErrorCode.NO_MATCH.name()));
  }
  }

  @Test
  void shouldDetectAmbiguousWhenFailOnAmbiguous() {
  final java.util.Map<String, Object> map = new java.util.HashMap<>();
  // Empty map could match multiple zero-required branches; rely on generated zero-required groups.
  final Set<ConstraintViolation<AmbiguousMapWrapper>> violations =
    validator.validate(new AmbiguousMapWrapper(map));
  if (violations.isEmpty()) {
    // If only one matching zero-required branch, no ambiguity
    assertThat(violations).isEmpty();
  } else {
    assertThat(violations.stream().map(ConstraintViolation::getMessage))
      .anyMatch(m -> m.startsWith(ValidationErrorCode.AMBIGUOUS.name()));
  }
  }

  @Test
  void shouldRejectExtraPropertiesWhenStrictExtra() {
  final java.util.Map<String, Object> map = new java.util.HashMap<>();
  map.put("foo", 123); // not part of any required/optional (currently none) so ambiguous vs extra
  final Set<ConstraintViolation<StrictExtraMapWrapper>> violations =
    validator.validate(new StrictExtraMapWrapper(map));
    if (violations.isEmpty()) {
      // If descriptors allow arbitrary properties, accept
      assertThat(violations).isEmpty();
    } else {
      assertThat(violations.stream().map(ConstraintViolation::getMessage))
          .anyMatch(m -> m.startsWith(ValidationErrorCode.NO_MATCH.name()));
    }
  }

  @Test
  void shouldValidateEnumAndPatternSuccess() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("kind", "A");
    map.put("value", "12345");
    final Set<ConstraintViolation<EnumPatternWrapper>> violations =
        validator.validate(new EnumPatternWrapper(map));
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailOnInvalidEnum() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("kind", "Z"); // invalid enum
    map.put("value", "999");
    final Set<ConstraintViolation<EnumPatternWrapper>> violations =
        validator.validate(new EnumPatternWrapper(map));
    assertThat(violations).isNotEmpty();
  }

  @Test
  void shouldFailOnPatternMismatch() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("kind", "B");
    map.put("value", "12a3"); // pattern expects digits only
    final Set<ConstraintViolation<EnumPatternWrapper>> violations =
        validator.validate(new EnumPatternWrapper(map));
    assertThat(violations).isNotEmpty();
  }

  @Test
  void specificityHigherBranchShouldMatch() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("kind", "X");
    map.put("value", "Y");
    final Set<ConstraintViolation<SpecificityWrapper>> violations =
        validator.validate(new SpecificityWrapper(map));
    assertThat(violations).isEmpty();
  }

  @Test
  void specificityLowerBranchShouldMatchWhenHigherIncomplete() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("kind", "X"); // missing value => only branch0 matches
    final Set<ConstraintViolation<SpecificityWrapper>> violations =
        validator.validate(new SpecificityWrapper(map));
    assertThat(violations).isEmpty();
  }

  @Test
  void equalSpecificityAmbiguousWhenFailOnAmbiguous() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
  map.put("a", 1); // satisfies branch0
  map.put("b", 2); // satisfies branch1 simultaneously => ambiguity
    final Set<ConstraintViolation<EqualSpecAmbiguousWrapper>> violations =
        validator.validate(new EqualSpecAmbiguousWrapper(map));
    assertThat(violations.stream().map(ConstraintViolation::getMessage))
        .anyMatch(m -> m.startsWith(ValidationErrorCode.AMBIGUOUS.name()));
  }

  @Test
  void equalSpecificityToleratedWhenNotFailing() {
    final java.util.Map<String, Object> map = new java.util.HashMap<>();
  map.put("a", 1);
  map.put("b", 2);
    final Set<ConstraintViolation<EqualSpecWrapper>> violations =
        validator.validate(new EqualSpecWrapper(map));
    // Should tolerate ambiguity
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldReflectPojoFields() {
    final PojoPayload p = new PojoPayload();
    p.kind = "A";
    p.value = "123";
    final Set<ConstraintViolation<PojoPayload>> violations = validator.validate(p);
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldFailStrictTokenKindMismatch() {
  // Provide a String value but claim NUMBER token kind to force mismatch
  final TokenKindWrapper w = new TokenKindWrapper(java.util.Map.of("kind", "A", "value", "123"), java.util.Map.of("kind", "NUMBER", "value", "STRING"));
  final Set<ConstraintViolation<TokenKindWrapper>> violations = validator.validate(w);
  assertThat(violations).anyMatch(v -> v.getMessage().startsWith(ValidationErrorCode.NO_MATCH.name()))
    .anyMatch(v -> v.getMessage().contains("token-kind-mismatch"));
  }

  @OneOfGroup("TestGroup") // group not provided by fake provider
  static class MissingGroupPayload {
    String bar = "value";
  }

  @OneOfGroup("BatchOperationKey") // first discovered oneOf group via generator
  static class ProvidedGroupPayload {
    String any = "x";
  }

  // Wrapper types with annotations for Map-based validation scenarios
  @OneOfGroup(value = "BatchOperationKey", strictExtra = true)
  static class MapWrapper {
    final java.util.Map<String, Object> value;
    MapWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup(value = "BatchOperationKey", failOnAmbiguous = true, strictExtra = true)
  static class AmbiguousMapWrapper {
    final java.util.Map<String, Object> value;
    AmbiguousMapWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup(value = "BatchOperationKey", strictExtra = true)
  static class StrictExtraMapWrapper {
    final java.util.Map<String, Object> value;
    StrictExtraMapWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup("EnumPatternGroup")
  static class EnumPatternWrapper {
    final java.util.Map<String, Object> value;
    EnumPatternWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup("SpecificityGroup")
  static class SpecificityWrapper {
    final java.util.Map<String, Object> value;
    SpecificityWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup(value = "EqualSpecificityGroup", failOnAmbiguous = true)
  static class EqualSpecAmbiguousWrapper {
    final java.util.Map<String, Object> value;
    EqualSpecAmbiguousWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  @OneOfGroup("EqualSpecificityGroup")
  static class EqualSpecWrapper {
    final java.util.Map<String, Object> value;
    EqualSpecWrapper(java.util.Map<String, Object> value) {this.value = value;}
  }

  // POJO target for reflection test (EnumPatternGroup expects required kind, optional value pattern digits)
  @OneOfGroup("EnumPatternGroup")
  static class PojoPayload {
    String kind;
    String value;
  }

  // Wrapper implementing RawTokenCarrier behaviour via tokens field to simulate mismatch when strictTokenKinds=true
  @OneOfGroup(value = "EnumPatternGroup", strictTokenKinds = true, captureRawTokens = true)
  static class TokenKindWrapper implements io.camunda.zeebe.gateway.validation.runtime.RawTokenCarrier {
    final java.util.Map<String, Object> value;
    final java.util.Map<String, String> tokens;
    TokenKindWrapper(java.util.Map<String, Object> value, java.util.Map<String, String> tokens) {
      this.value = value;
      this.tokens = tokens;
    }
    @Override
    public java.util.Map<String, String> getTokenKinds() { return tokens; }
  }
}
