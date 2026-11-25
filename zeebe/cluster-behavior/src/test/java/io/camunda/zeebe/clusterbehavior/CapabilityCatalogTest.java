/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.clusterbehavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The failure messages say what is wrong and how to fix the catalog, because the people who break
 * these tests are the ones adding an entry to {@link CapabilityCatalog}, not the ones who know how
 * the tests work.
 */
final class CapabilityCatalogTest {

  // the parameterized tests have no invocation as long as the catalog has no entry
  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(CapabilityCatalog.class)
  void shouldHaveANonNegativeOrdinal(final CapabilityCatalog capability) {
    assertThat(capability.getValue())
        .as(
            "%s has the ordinal %d, but an ordinal must not be negative. Give it the next free "
                + "ordinal: one higher than the highest ordinal in CapabilityCatalog.",
            capability, capability.getValue())
        .isNotNegative();
  }

  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(CapabilityCatalog.class)
  void shouldOnlyRequireCapabilitiesWithALowerOrdinal(final CapabilityCatalog capability) {
    assertThat(capability.requirements())
        .allSatisfy(
            required ->
                assertThat(required.getValue())
                    .as(
                        "%s (ordinal %d) requires %s (ordinal %d), but a requirement must have a "
                            + "lower ordinal than the capability that requires it. Declare %s "
                            + "before %s in CapabilityCatalog with a lower ordinal, or remove the "
                            + "requirement if %s does not depend on it.",
                        capability,
                        capability.getValue(),
                        required,
                        required.getValue(),
                        required,
                        capability,
                        capability)
                    .isLessThan(capability.getValue()));
  }

  @ParameterizedTest(allowZeroInvocations = true)
  @EnumSource(CapabilityCatalog.class)
  void shouldBeFoundByItsOrdinal(final CapabilityCatalog capability) {
    assertThat(CapabilityCatalog.isKnown(capability.getValue()))
        .as(
            "isKnown(%d) is false, but %s has this ordinal. The lookup index is derived from "
                + "CapabilityCatalog.values(), so the catalog class is inconsistent: check that "
                + "the entry is declared in CapabilityCatalog.",
            capability.getValue(), capability)
        .isTrue();
    assertThat(CapabilityCatalog.from(capability.getValue()))
        .as(
            "from(%d) did not return %s. Another entry of CapabilityCatalog has the same ordinal: "
                + "give %s the next free ordinal and never reuse an ordinal.",
            capability.getValue(), capability, capability)
        .isSameAs(capability);
  }

  @Test
  void shouldDeclareTheEntriesInTheOrderOfTheirOrdinals() {
    final var capabilities = CapabilityCatalog.values();
    final var ordinals =
        Arrays.stream(capabilities).mapToInt(CapabilityCatalog::getValue).toArray();
    final var declared =
        Arrays.stream(capabilities)
            .map(capability -> capability + "=" + capability.getValue())
            .collect(Collectors.joining(", "));

    assertThat(ordinals)
        .as(
            "Two entries of CapabilityCatalog have the same ordinal. Declared: [%s]. Give the "
                + "entry you added the next free ordinal, one higher than the highest one. Never "
                + "change or reuse the ordinal of an existing entry: ordinals are persisted.",
            declared)
        .doesNotHaveDuplicates();
    assertThat(ordinals)
        .as(
            "The entries of CapabilityCatalog must be declared in increasing order of their "
                + "ordinal. Declared: [%s]. Move the entry you added to the end of "
                + "CapabilityCatalog, or to its place in the order of the ordinals. Never change "
                + "the ordinal of an existing entry: ordinals are persisted.",
            declared)
        .isSorted();
  }

  @Test
  void shouldRejectAnOrdinalThatIsNotInTheCatalog() {
    assertThat(CapabilityCatalog.isKnown(Integer.MAX_VALUE))
        .as(
            "CapabilityCatalog has an entry with the ordinal %d, which this test uses as an "
                + "ordinal that is not in the catalog. That is most likely a mistake in the "
                + "ordinal of the entry: give it the next free ordinal.",
            Integer.MAX_VALUE)
        .isFalse();
    assertThatThrownBy(() -> CapabilityCatalog.from(Integer.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected a capability ordinal of the catalog but got " + Integer.MAX_VALUE);
  }
}
