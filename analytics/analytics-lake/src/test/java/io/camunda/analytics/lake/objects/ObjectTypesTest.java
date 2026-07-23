/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ObjectTypes}' declaration builder: validation rules and compiled shape. */
class ObjectTypesTest {

  @Test
  void shouldCompileAVariableIdentifiedType() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("orderId")).build();

    // then
    assertThat(type.name()).isEqualTo("order");
    assertThat(type.identifiers()).hasSize(1);
    assertThat(type.identifiers().get(0))
        .isEqualTo(new IdentifierSource.VariableIdentifier("orderId"));
  }

  @Test
  void shouldCompileACorrelationKeyIdentifiedType() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.correlationKey("orderId")).build();

    // then
    assertThat(type.identifiers().get(0))
        .isEqualTo(new IdentifierSource.CorrelationKeyIdentifier("orderId"));
  }

  @Test
  void shouldCompileATypeWithMultipleIdentifiers() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .identifiedBy(ObjectTypes.correlationKey("orderId"))
            .build();

    // then
    assertThat(type.identifiers()).hasSize(2);
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(
            () -> ObjectTypes.declare("  ").identifiedBy(ObjectTypes.variable("x")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(
            () -> ObjectTypes.declare(null).identifiedBy(ObjectTypes.variable("x")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectNoIdentifiers() {
    assertThatThrownBy(() -> ObjectTypes.declare("order").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declares no identifier");
  }

  @Test
  void shouldRejectNullIdentifierSource() {
    assertThatThrownBy(() -> ObjectTypes.declare("order").identifiedBy(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectBlankVariableIdentifierName() {
    assertThatThrownBy(
            () -> ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable(" ")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectBlankCorrelationKeyLabel() {
    assertThatThrownBy(
            () -> ObjectTypes.declare("order").identifiedBy(ObjectTypes.correlationKey("")).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  // ---- closing rules -----------------------------------------------------------------------

  @Test
  void shouldDefaultToNoClosingRulesWhenNoneDeclared() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();

    // then: the default-open case
    assertThat(type.closingRules()).isEmpty();
  }

  @Test
  void shouldCompileATypeWithAClosingRule() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .closes(ObjectTypes.onProcessCompletion("orderFulfillment"))
            .build();

    // then
    assertThat(type.closingRules())
        .containsExactly(new ClosingRule.OnProcessCompletion("orderFulfillment"));
  }

  @Test
  void shouldCompileATypeWithMultipleClosingRules() {
    // when: an object type may declare more than one closing process
    final CompiledObjectType type =
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .closes(ObjectTypes.onProcessCompletion("processA"))
            .closes(ObjectTypes.onProcessCompletion("processB"))
            .build();

    // then
    assertThat(type.closingRules()).hasSize(2);
  }

  @Test
  void shouldRejectNullClosingRule() {
    assertThatThrownBy(() -> ObjectTypes.declare("order").closes(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectBlankClosingProcessId() {
    assertThatThrownBy(
            () ->
                ObjectTypes.declare("order")
                    .identifiedBy(ObjectTypes.variable("orderId"))
                    .closes(ObjectTypes.onProcessCompletion(" "))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectNullClosingProcessId() {
    assertThatThrownBy(
            () ->
                ObjectTypes.declare("order")
                    .identifiedBy(ObjectTypes.variable("orderId"))
                    .closes(ObjectTypes.onProcessCompletion(null))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  // ---- belongsTo ----------------------------------------------------------------------------

  @Test
  void shouldDefaultToNoParentTypeWhenNoneDeclared() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();

    // then: the common case -- no belongsTo declared
    assertThat(type.parentTypeName()).isNull();
  }

  @Test
  void shouldCompileATypeWithABelongsToParent() {
    // when
    final CompiledObjectType type =
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .belongsTo("customer")
            .build();

    // then
    assertThat(type.parentTypeName()).isEqualTo("customer");
  }

  @Test
  void shouldRejectNullBelongsToParentTypeName() {
    assertThatThrownBy(() -> ObjectTypes.declare("order").belongsTo(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectBlankBelongsToParentTypeName() {
    assertThatThrownBy(
            () ->
                ObjectTypes.declare("order")
                    .identifiedBy(ObjectTypes.variable("orderId"))
                    .belongsTo(" ")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectSelfReferencingBelongsTo() {
    assertThatThrownBy(
            () ->
                ObjectTypes.declare("order")
                    .identifiedBy(ObjectTypes.variable("orderId"))
                    .belongsTo("order")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("self-reference");
  }
}
