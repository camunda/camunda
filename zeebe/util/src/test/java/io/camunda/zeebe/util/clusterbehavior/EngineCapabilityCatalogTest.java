/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.clusterbehavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Consistency checks of the {@link EngineCapabilityCatalog}. They are unit tests so that they fail
 * the build in Maven and Gradle alike.
 */
final class EngineCapabilityCatalogTest {

  // the parameterized tests have no invocation as long as the catalog has no entry
  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(EngineCapabilityCatalog.class)
  void shouldHaveANonNegativeOrdinal(final EngineCapabilityCatalog capability) {
    assertThat(capability.capabilityOrdinal()).isNotNegative();
  }

  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(EngineCapabilityCatalog.class)
  void shouldOnlyRequireCapabilitiesWithALowerOrdinal(final EngineCapabilityCatalog capability) {
    assertThat(capability.requirements())
        .allSatisfy(
            required ->
                assertThat(required.capabilityOrdinal())
                    .as("ordinal of %s required by %s", required, capability)
                    .isLessThan(capability.capabilityOrdinal()));
  }

  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(EngineCapabilityCatalog.class)
  void shouldBeFoundByItsOrdinal(final EngineCapabilityCatalog capability) {
    assertThat(EngineCapabilityCatalog.isKnown(capability.capabilityOrdinal())).isTrue();
    assertThat(EngineCapabilityCatalog.from(capability.capabilityOrdinal())).isSameAs(capability);
  }

  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(EngineCapabilityCatalog.class)
  void shouldHaveImmutableRequirements(final EngineCapabilityCatalog capability) {
    assertThatThrownBy(() -> capability.requirements().add(capability))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldDeclareTheEntriesInTheOrderOfTheirOrdinals() {
    // the unique ordinals are already enforced when the catalog is initialized
    final var ordinals =
        Arrays.stream(EngineCapabilityCatalog.values())
            .mapToInt(EngineCapabilityCatalog::capabilityOrdinal)
            .toArray();

    assertThat(ordinals).isSorted();
  }

  @Test
  void shouldRejectAnOrdinalThatIsNotInTheCatalog() {
    assertThat(EngineCapabilityCatalog.isKnown(Integer.MAX_VALUE)).isFalse();
    assertThatThrownBy(() -> EngineCapabilityCatalog.from(Integer.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
