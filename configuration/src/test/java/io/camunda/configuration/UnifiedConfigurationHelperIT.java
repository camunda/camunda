/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ResolvableType;
import org.springframework.util.unit.DataSize;

@SpringBootTest(
    classes = {UnifiedConfigurationHelper.class},
    properties = {"spring.config.location=classpath:test-config.yaml"})
class UnifiedConfigurationHelperIT {

  @Nested
  class WithOnlyNewSet {
    @Test
    void shouldReturnListFromNewWhenNewConfigUsesMultilineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinelistsyntax",
              List.of("newItem1", "newItem2"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(legacyList).containsExactlyInAnyOrder("newItem1", "newItem2");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntax",
              List.of("newItem3", "newItem4"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(legacyList).containsExactlyInAnyOrder("newItem3", "newItem4");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntaxwithoutqoutes",
              List.of("newItem7", "newItem8"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(legacyList).containsExactlyInAnyOrder("newItem7", "newItem8");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesCommaSeparatedString() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".commaseparatedstring",
              List.of("newItem5", "newItem6"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(legacyList).containsExactlyInAnyOrder("newItem5", "newItem6");
    }
  }

  @Nested
  class WithOnlyLegacySet {
    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(legacyList).containsExactlyInAnyOrder("legacyItem1", "legacyItem2");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntax"));
      assertThat(legacyList).containsExactlyInAnyOrder("legacyItem3", "legacyItem4");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntaxwithoutqoutes"));
      assertThat(legacyList).containsExactlyInAnyOrder("legacyItem7", "legacyItem8");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparatedstring"));
      assertThat(legacyList).containsExactlyInAnyOrder("legacyItem5", "legacyItem6");
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<Duration> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.durationsmultilinesyntax"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(10), Duration.ofMinutes(20), Duration.ofHours(30));
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<Duration> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.durationinlinesyntax"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(40), Duration.ofMinutes(50), Duration.ofHours(60));
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<Duration> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparatedduration"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(70), Duration.ofMinutes(80), Duration.ofHours(90));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<DataSize> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.datasizemultilinesyntax"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(10), DataSize.ofMegabytes(20), DataSize.ofGigabytes(30));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<DataSize> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.datasizeinlinesyntax"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(40), DataSize.ofMegabytes(50), DataSize.ofGigabytes(60));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<DataSize> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparateddatasize"));
      assertThat(legacyList)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(70), DataSize.ofMegabytes(80), DataSize.ofGigabytes(90));
    }
  }

  @Nested
  class WithNewAndLegacySet {
    @Test
    void shouldReturnListFromNewWhenNewConfigUsesMultilineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinelistsyntax",
              List.of("newItem1", "newItem2"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(legacyList).containsExactlyInAnyOrder("newItem1", "newItem2");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntax() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntax",
              List.of("newItem3", "newItem4"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(legacyList).containsExactlyInAnyOrder("newItem3", "newItem4");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntaxwithoutqoutes",
              List.of("newItem7", "newItem8"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntaxwithoutqoutes"));
      assertThat(legacyList).containsExactlyInAnyOrder("newItem7", "newItem8");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesCommaSeparatedString() {
      final List<String> legacyList =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".commaseparatedstring",
              List.of("newItem5", "newItem6"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(legacyList).containsExactlyInAnyOrder("newItem5", "newItem6");
    }
  }
}
