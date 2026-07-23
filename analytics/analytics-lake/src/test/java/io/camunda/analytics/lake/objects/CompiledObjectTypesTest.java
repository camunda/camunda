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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompiledObjectTypes#of}'s cross-type validation and lookups — see its own
 * class javadoc for the full list of rules under test.
 */
class CompiledObjectTypesTest {

  @Test
  void shouldRejectEmptyDeclarationList() {
    assertThatThrownBy(() -> CompiledObjectTypes.of(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one object type");
  }

  @Test
  void shouldResolveVariableIdentifiedType() {
    // given
    final CompiledObjectType order =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("orderId")).build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(order);

    // then
    assertThat(registry.variableIdentifiedType("orderId")).isEqualTo(order);
    assertThat(registry.variableIdentifiedType("somethingElse")).isNull();
  }

  @Test
  void shouldReturnNullCorrelationKeyTypeWhenNoneDeclaresIt() {
    // given
    final CompiledObjectType order =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("orderId")).build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(order);

    // then
    assertThat(registry.correlationKeyIdentifiedType()).isNull();
  }

  @Test
  void shouldResolveTheSingleCorrelationKeyIdentifiedType() {
    // given
    final CompiledObjectType dispute =
        ObjectTypes.declare("dispute")
            .identifiedBy(ObjectTypes.correlationKey("correlationKey"))
            .build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(dispute);

    // then
    assertThat(registry.correlationKeyIdentifiedType()).isEqualTo(dispute);
  }

  @Test
  void shouldRejectTwoTypesWithTheSameName() {
    // given
    final CompiledObjectType a =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("x")).build();
    final CompiledObjectType b =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("y")).build();

    // then
    assertThatThrownBy(() -> CompiledObjectTypes.of(a, b))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declared more than once");
  }

  @Test
  void shouldRejectTwoTypesClaimingTheSameVariableName() {
    // given: both types identify by the exact same variable name -- ambiguous
    final CompiledObjectType order =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.variable("id")).build();
    final CompiledObjectType shipment =
        ObjectTypes.declare("shipment").identifiedBy(ObjectTypes.variable("id")).build();

    // then
    assertThatThrownBy(() -> CompiledObjectTypes.of(order, shipment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ambiguous");
  }

  @Test
  void shouldRejectTwoTypesDeclaringCorrelationKeyIdentity() {
    // given: the v1 fail-fast limit -- at most one object type may claim correlation-key identity
    final CompiledObjectType order =
        ObjectTypes.declare("order").identifiedBy(ObjectTypes.correlationKey("orderId")).build();
    final CompiledObjectType shipment =
        ObjectTypes.declare("shipment")
            .identifiedBy(ObjectTypes.correlationKey("shipmentId"))
            .build();

    // then
    assertThatThrownBy(() -> CompiledObjectTypes.of(order, shipment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlation-key identity");
  }

  @Test
  void shouldAllowDistinctTypesWithDistinctVariableNames() {
    // given
    final CompiledObjectType customer =
        ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();
    final CompiledObjectType dispute =
        ObjectTypes.declare("dispute").identifiedBy(ObjectTypes.variable("correlationKey")).build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(customer, dispute);

    // then
    assertThat(registry.variableIdentifiedType("customerId")).isEqualTo(customer);
    assertThat(registry.variableIdentifiedType("correlationKey")).isEqualTo(dispute);
  }

  // ---- closing-type lookup ---------------------------------------------------------------------

  @Test
  void shouldReturnEmptyClosingTypesForANonDeclaredProcess() {
    // given: a type declaring no closing rule at all (the default-open case)
    final CompiledObjectType customer =
        ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(customer);

    // then
    assertThat(registry.closingTypesForProcess("anyProcess")).isEmpty();
  }

  @Test
  void shouldResolveTheDeclaringTypeForItsClosingProcess() {
    // given
    final CompiledObjectType dispute =
        ObjectTypes.declare("dispute")
            .identifiedBy(ObjectTypes.variable("correlationKey"))
            .closes(ObjectTypes.onProcessCompletion("disputeHandling"))
            .build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(dispute);

    // then
    assertThat(registry.closingTypesForProcess("disputeHandling")).containsExactly(dispute);
    assertThat(registry.closingTypesForProcess("someOtherProcess")).isEmpty();
  }

  @Test
  void shouldResolveMultipleTypesClosingOnTheSameProcess() {
    // given: two independently declared types both closing on the same process completion
    final CompiledObjectType order =
        ObjectTypes.declare("order")
            .identifiedBy(ObjectTypes.variable("orderId"))
            .closes(ObjectTypes.onProcessCompletion("orderFulfillment"))
            .build();
    final CompiledObjectType shipment =
        ObjectTypes.declare("shipment")
            .identifiedBy(ObjectTypes.variable("shipmentId"))
            .closes(ObjectTypes.onProcessCompletion("orderFulfillment"))
            .build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(order, shipment);

    // then
    assertThat(registry.closingTypesForProcess("orderFulfillment"))
        .containsExactlyInAnyOrder(order, shipment);
  }

  @Test
  void shouldResolveATypeDeclaringMultipleClosingProcessesUnderEachOne() {
    // given
    final CompiledObjectType dispute =
        ObjectTypes.declare("dispute")
            .identifiedBy(ObjectTypes.variable("correlationKey"))
            .closes(ObjectTypes.onProcessCompletion("processA"))
            .closes(ObjectTypes.onProcessCompletion("processB"))
            .build();

    // when
    final CompiledObjectTypes registry = CompiledObjectTypes.of(dispute);

    // then
    assertThat(registry.closingTypesForProcess("processA")).containsExactly(dispute);
    assertThat(registry.closingTypesForProcess("processB")).containsExactly(dispute);
  }
}
