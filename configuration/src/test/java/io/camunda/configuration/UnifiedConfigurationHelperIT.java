/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinelistsyntax",
              List.of("newItem1", "newItem2"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(list).containsExactlyInAnyOrder("newItem1", "newItem2");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntax() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntax",
              List.of("newItem3", "newItem4"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(list).containsExactlyInAnyOrder("newItem3", "newItem4");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntaxwithoutqoutes",
              List.of("newItem7", "newItem8"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(list).containsExactlyInAnyOrder("newItem7", "newItem8");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesCommaSeparatedString() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".commaseparatedstring",
              List.of("newItem5", "newItem6"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(list).containsExactlyInAnyOrder("newItem5", "newItem6");
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesMultilineMapSyntax() {
      final var expectedMap = Map.of("k1new", 1, "k2new", 2, "k3new", 3);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesInlineMapSyntax() {
      final var expectedMap = Map.of("k4new", 4, "k5new", 5, "k6new", 6);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesSinglelineMapSyntax() {
      final var expectedMap = Map.of("k7new", 7, "k8new", 8, "k9new", 9);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".singlelinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Collections.emptySet());
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }
  }

  @Nested
  class WithOnlyLegacySet {
    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(list).containsExactlyInAnyOrder("legacyItem1", "legacyItem2");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntax"));
      assertThat(list).containsExactlyInAnyOrder("legacyItem3", "legacyItem4");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntaxwithoutqoutes"));
      assertThat(list).containsExactlyInAnyOrder("legacyItem7", "legacyItem8");
    }

    @Test
    void shouldReturnListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparatedstring"));
      assertThat(list).containsExactlyInAnyOrder("legacyItem5", "legacyItem6");
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<Duration> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.durationsmultilinesyntax"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(10), Duration.ofMinutes(20), Duration.ofHours(30));
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<Duration> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.durationinlinesyntax"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(40), Duration.ofMinutes(50), Duration.ofHours(60));
    }

    @Test
    void shouldReturnDurationListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<Duration> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, Duration.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparatedduration"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              Duration.ofSeconds(70), Duration.ofMinutes(80), Duration.ofHours(90));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesMultilineListSyntax() {
      final List<DataSize> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.datasizemultilinesyntax"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(10), DataSize.ofMegabytes(20), DataSize.ofGigabytes(30));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesInlineListSyntax() {
      final List<DataSize> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.datasizeinlinesyntax"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(40), DataSize.ofMegabytes(50), DataSize.ofGigabytes(60));
    }

    @Test
    void shouldReturnDataSizeListFromLegacyWhenLegacyConfigUsesCommaSeparatedString() {
      final List<DataSize> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyList(),
              ResolvableType.forClassWithGenerics(List.class, DataSize.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.commaseparateddatasize"));
      assertThat(list)
          .containsExactlyInAnyOrder(
              DataSize.ofBytes(70), DataSize.ofMegabytes(80), DataSize.ofGigabytes(90));
    }

    @Test
    void shouldReturnMapFromLegacyWhenLegacyConfigUsesMultilineMapSyntax() {
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyMap(),
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinemapsyntax"));
      assertThat(map)
          .containsExactly(entry("k1legacy", 1), entry("k2legacy", 2), entry("k3legacy", 3));
    }

    @Test
    void shouldReturnMapFromLegacyWhenLegacyConfigUsesInlineMapSyntax() {
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyMap(),
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinemapsyntax"));
      assertThat(map)
          .containsExactly(entry("k4legacy", 4), entry("k5legacy", 5), entry("k6legacy", 6));
    }

    @Test
    void shouldReturnMapFromLegacyWhenLegacyConfigUsesSinglelineMapSyntax() {
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "foo" + ".bar",
              Collections.emptyMap(),
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.singlelinemapsyntax"));
      assertThat(map)
          .containsExactly(entry("k7legacy", 7), entry("k8legacy", 8), entry("k9legacy", 9));
    }
  }

  @Nested
  class WithNewAndLegacySet {
    @Test
    void shouldReturnListFromNewWhenNewConfigUsesMultilineListSyntax() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinelistsyntax",
              List.of("newItem1", "newItem2"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(list).containsExactlyInAnyOrder("newItem1", "newItem2");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntax() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntax",
              List.of("newItem3", "newItem4"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(list).containsExactlyInAnyOrder("newItem3", "newItem4");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesInlineListSyntaxWithoutQuotes() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinelistsyntaxwithoutqoutes",
              List.of("newItem7", "newItem8"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinelistsyntaxwithoutqoutes"));
      assertThat(list).containsExactlyInAnyOrder("newItem7", "newItem8");
    }

    @Test
    void shouldReturnListFromNewWhenNewConfigUsesCommaSeparatedString() {
      final List<String> list =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".commaseparatedstring",
              List.of("newItem5", "newItem6"),
              ResolvableType.forClassWithGenerics(List.class, String.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinelistsyntax"));
      assertThat(list).containsExactlyInAnyOrder("newItem5", "newItem6");
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesMultilineMapSyntax() {
      final var expectedMap = Map.of("k1new", 1, "k2new", 2, "k3new", 3);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".multilinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.multilinemapsyntax"));
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesInlineMapSyntax() {
      final var expectedMap = Map.of("k4new", 4, "k5new", 5, "k6new", 6);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".inlinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.inlinemapsyntax"));
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }

    @Test
    void shouldReturnMapFromNewWhenNewConfigUsesSinglelineMapSyntax() {
      final var expectedMap = Map.of("k7new", 7, "k8new", 8, "k9new", 9);
      final Map<String, Integer> map =
          UnifiedConfigurationHelper.validateLegacyConfiguration(
              "new" + ".singlelinemapsyntax",
              expectedMap,
              ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
              BackwardsCompatibilityMode.SUPPORTED,
              Set.of("legacy.singlelinemapsyntax"));
      assertThat(map).containsExactlyInAnyOrderEntriesOf(expectedMap);
    }
  }
}
