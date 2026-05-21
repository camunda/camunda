/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.exporter.api.context.StrictConfiguration;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterConfigurationTest {

  // ---------------------------------------------------------------------------
  // fromArgs — key normalization
  // ---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        // kebab-case (hyphens stripped by normalizeKey)
        "number-of-shards",
        // camelCase (lowercased by normalizeKey)
        "numberOfShards",
        // uppercase kebab-case (hyphens stripped + lowercased by normalizeKey)
        "NUMBER-OF-SHARDS",
        // all-lowercase concatenated — the form produced by Spring Boot env var lowercasing
        // e.g. ARGS_NUMBEROFSHARDS → Spring lowercases → numberofshards
        "numberofshards",
        // uppercase concatenated — matched via lowercase normalisation
        "NUMBEROFSHARDS"
      })
  void shouldInstantiateConfigWithCaseInsensitiveProperties(final String property) {
    // given
    final var args = Map.<String, Object>of(property, 1);
    final var expected = new Config(1);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(Config.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "number-of-shards",
        "numberOfShards",
        "NUMBER-OF-SHARDS",
        "numberofshards",
        "NUMBEROFSHARDS"
      })
  void shouldInstantiateNestedConfigWithCaseInsensitiveProperties(final String property) {
    // given
    final var args = Map.<String, Object>of("nested", Map.of(property, 1));
    final var expected = new ContainerConfig(new Config(1));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(ContainerConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @Test
  void shouldPreserveMapPropertyKeys() {
    // given — only object property names should be normalized; map keys are user data and must stay
    // intact
    final var args =
        Map.<String, Object>of("LABELS", Map.of("Tenant-A", "alpha", "tenant-b", "beta"));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(LabelsConfig.class);

    // then
    assertThat(instance)
        .isEqualTo(new LabelsConfig(Map.of("Tenant-A", "alpha", "tenant-b", "beta")));
  }

  @Test
  void shouldNormalizeObjectValuesInsideMapProperty() {
    // given — map keys are data and must stay unchanged, but nested object properties inside each
    // value should still be normalized
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of(
                "tenant-A", Map.of("NUMBER-OF-SHARDS", 1),
                "tenant-B", Map.of("numberOfShards", 2)));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(NamedConfigs.class);

    // then
    assertThat(instance)
        .isEqualTo(new NamedConfigs(Map.of("tenant-A", new Config(1), "tenant-B", new Config(2))));
  }

  @Test
  void shouldNormalizeKeysInsideArrayValues() {
    // given — camelCase keys inside maps that are elements of a Java array (not an Iterable)
    final var element0 = Map.<String, Object>of("numberOfShards", 0);
    final var element1 = Map.<String, Object>of("numberOfShards", 1);
    // Object[] triggers the array branch in normalizeValue, distinct from the Iterable branch
    final var args = Map.<String, Object>of("configs", new Object[] {element0, element1});
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(ListConfig.class);

    // then
    assertThat(instance).isEqualTo(new ListConfig(List.of(new Config(0), new Config(1))));
  }

  @Test
  void shouldPreserveNullValuesInArgs() {
    // given — null value in args map must pass through normalizeValue unchanged
    final Map<String, Object> args = new HashMap<>();
    args.put("timeout", null);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = ExporterConfiguration.fromArgs(DurationConfig.class, args);

    // then
    assertThat(instance.timeout()).isNull();
  }

  // ---------------------------------------------------------------------------
  // fromArgs — null map
  // ---------------------------------------------------------------------------

  @Test
  void shouldInstantiateDefaultsWhenArgsNull() {
    // given — null map triggers ReflectUtil.newInstance which uses the no-arg constructor
    // when
    final var instance = ExporterConfiguration.fromArgs(DefaultableConfig.class, null);

    // then
    assertThat(instance.count).isEqualTo(42);
  }

  // ---------------------------------------------------------------------------
  // fromArgs — error handling
  // ---------------------------------------------------------------------------

  @Test
  void shouldInstantiateConfigWithUnknownProperty() {
    // given — unannotated config class: unknown properties are silently ignored
    final var args = Map.<String, Object>of("number-of-shards", 1, "unknownProp", false);
    final var expected = new Config(1);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(Config.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @Test
  void shouldRejectStrictConfigWithUnknownProperty() {
    // given — @StrictConfiguration: unknown properties must be rejected
    final var args = Map.<String, Object>of("number-of-shards", 1, "unknownProp", false);
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(StrictConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownprop")
        .hasMessageContaining("StrictConfig")
        .hasMessageContaining("path:");
  }

  @Test
  void shouldReportNestedClassNameWhenNestedPropertyIsUnrecognized() {
    // given — the typo is inside the nested object, so the error must mention the nested class
    // (StrictConfig), not the outer class (StrictContainerConfig)
    final var args = Map.<String, Object>of("nested", Map.of("unknownProp", false));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(StrictContainerConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknownprop")
        // The error message must name the actual class where the typo lives, not the outer wrapper
        .hasMessageContaining("StrictConfig")
        // The full JSON path must be present so users can locate the typo quickly
        .hasMessageContaining("path:");
  }

  @Test
  void shouldRethrowOnTypeConversionError() {
    // given — type mismatch (string for int field) causes a non-UnrecognizedPropertyException;
    // fromArgs must re-throw the original exception without wrapping it as an "unknown property"
    final var args = Map.<String, Object>of("number-of-shards", "not-a-number");

    // when - then
    assertThatCode(() -> ExporterConfiguration.fromArgs(Config.class, args))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("Unknown exporter configuration property");
  }

  // ---------------------------------------------------------------------------
  // asArgs — serialization
  // ---------------------------------------------------------------------------

  @Test
  void shouldSerializeConfigToCamelCaseArgs() {
    // given
    final var config = new Config(5);

    // when
    final var args = ExporterConfiguration.asArgs(config);

    // then — field "numberOfShards" is serialized as-is (camelCase, no naming strategy)
    assertThat(args).containsEntry("numberOfShards", 5);
  }

  // ---------------------------------------------------------------------------
  // Duration module
  // ---------------------------------------------------------------------------

  @Test
  void shouldDeserializeDurationFromEmbeddedObject() {
    // given — Duration is passed as a Java object (not a string) in the args map;
    // Jackson's convertValue goes through a TokenBuffer, so the serializer writes an embedded
    // object and the deserializer reads it back via the VALUE_EMBEDDED_OBJECT path
    final var duration = Duration.ofSeconds(30);
    final var args = Map.<String, Object>of("timeout", duration);

    // when
    final var instance = ExporterConfiguration.fromArgs(DurationConfig.class, args);

    // then
    assertThat(instance.timeout()).isEqualTo(duration);
  }

  @Test
  void shouldDeserializeDurationFromIsoString() {
    // given — Duration arrives as an ISO-8601 string from Spring Boot YAML properties
    final var args = Map.<String, Object>of("timeout", "PT30S");

    // when
    final var instance = ExporterConfiguration.fromArgs(DurationConfig.class, args);

    // then
    assertThat(instance.timeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldReturnNullDurationForEmptyString() {
    // given — empty string is the value Spring Boot sets when no duration is configured
    final var args = Map.<String, Object>of("timeout", "");

    // when
    final var instance = ExporterConfiguration.fromArgs(DurationConfig.class, args);

    // then
    assertThat(instance.timeout()).isNull();
  }

  @Test
  void shouldRoundTripDurationViaArgs() {
    // given
    final var original = new DurationConfig(Duration.ofMillis(500));

    // when — serialize to args map then deserialize back
    final var args = ExporterConfiguration.asArgs(original);
    final var roundTripped = ExporterConfiguration.fromArgs(DurationConfig.class, args);

    // then
    assertThat(roundTripped).isEqualTo(original);
  }

  // ---------------------------------------------------------------------------
  // Path module
  // ---------------------------------------------------------------------------

  @Test
  void shouldRoundTripRelativePath() {
    // given — relative path must be preserved without resolution to an absolute path
    final var original = new PathConfig(Path.of("relative/path/to/file"));

    // when
    final var args = ExporterConfiguration.asArgs(original);
    final var roundTripped = ExporterConfiguration.fromArgs(PathConfig.class, args);

    // then
    assertThat(roundTripped.filePath()).isEqualTo(original.filePath());
    assertThat(roundTripped.filePath().isAbsolute()).isFalse();
  }

  @Test
  void shouldRoundTripAbsolutePath() {
    // given
    final var original = new PathConfig(Path.of("/absolute/path/to/file"));

    // when
    final var args = ExporterConfiguration.asArgs(original);
    final var roundTripped = ExporterConfiguration.fromArgs(PathConfig.class, args);

    // then
    assertThat(roundTripped.filePath()).isEqualTo(original.filePath());
    assertThat(roundTripped.filePath().isAbsolute()).isTrue();
  }

  @Test
  void shouldSerializePathFieldToCamelCaseKey() {
    // given — field "filePath" is serialized as-is (camelCase, no naming strategy)
    final var config = new PathConfig(Path.of("some/path"));

    // when
    final var args = ExporterConfiguration.asArgs(config);

    // then
    assertThat(args).containsKey("filePath");
  }

  // ---------------------------------------------------------------------------
  // ConfigurationMapper (of / apply / get / toArgs)
  // ---------------------------------------------------------------------------

  @Test
  void shouldCreateConfigurationMapperViaOf() {
    // given
    final var args = Map.<String, Object>of("count", 5);

    // when
    final var mapper = ExporterConfiguration.of(MutableConfig.class, args);

    // then
    assertThat(mapper.get().count).isEqualTo(5);
  }

  @Test
  void shouldApplyConsumerViaMapper() {
    // given
    final var mapper = ExporterConfiguration.of(MutableConfig.class, Map.of("count", 5));

    // when
    final var returned = mapper.apply(c -> c.count = 99);

    // then — apply() must return the same mapper instance for chaining
    assertThat(returned).isSameAs(mapper);
    assertThat(mapper.get().count).isEqualTo(99);
  }

  @Test
  void shouldConvertConfigToArgsViaMapper() {
    // given
    final var mapper = ExporterConfiguration.of(MutableConfig.class, Map.of("count", 5));
    mapper.apply(c -> c.count = 7);

    // when
    final var args = mapper.toArgs();

    // then
    assertThat(args).containsEntry("count", 7);
  }

  // ---------------------------------------------------------------------------
  // Regression: Spring Boot indexed list properties (issue #4552)
  // ---------------------------------------------------------------------------

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldInstantiateMapOfIntegersAsList() {
    // given
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("0", Map.of("number-of-shards", 0), "1", Map.of("number-of-shards", 1)));
    final var expected = new ListConfig(List.of(new Config(0), new Config(1)));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(ListConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldInstantiateMapOfIntegersAsListNested() {
    // given
    final var serializedConfigs =
        Map.<String, Object>of(
            "0", Map.of("number-of-shards", 0), "1", Map.of("number-of-shards", 1));
    final var args =
        Map.<String, Object>of("list", Map.of("0", Map.of("configs", serializedConfigs)));
    final var expected =
        new NestedListConfig(List.of(new ListConfig(List.of(new Config(0), new Config(1)))));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(NestedListConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldNotInstantiateSparseList() {
    // given — list indices start at 1 instead of 0, so insertion at index 1 fails
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("1", Map.of("number-of-shards", 0), "2", Map.of("number-of-shards", 1)));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(ListConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(IndexOutOfBoundsException.class);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldNotInstantiateNonIntegerList() {
    // given — list indices are non-integer strings, so parsing to int fails
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("foo", Map.of("number-of-shards", 0), "bar", Map.of("number-of-shards", 1)));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(ListConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(NumberFormatException.class);
  }

  @Test
  void shouldRejectMixedIndexedAndNamedKeysForListProperties() {
    // given — mixed numeric and named keys are ambiguous for list-style map conversion
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of(
                "0", Map.of("number-of-shards", 0),
                "tenant-a", Map.of("number-of-shards", 1)));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(ListConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot mix indexed (numeric) and named keys");
  }

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  private record Config(int numberOfShards) {}

  @StrictConfiguration
  private record StrictConfig(int numberOfShards) {}

  @StrictConfiguration
  private record StrictContainerConfig(StrictConfig nested) {}

  private record ContainerConfig(Config nested) {}

  private record LabelsConfig(Map<String, String> labels) {}

  private record NamedConfigs(Map<String, Config> configs) {}

  private record ListConfig(List<Config> configs) {}

  private record NestedListConfig(List<ListConfig> list) {}

  private record DurationConfig(Duration timeout) {}

  private record PathConfig(Path filePath) {}

  /**
   * POJO (not a record) so that {@code ReflectUtil.newInstance} can use the no-arg constructor.
   * Must be {@code public} so that the generated default constructor is public and accessible via
   * reflection without {@code setAccessible(true)}.
   */
  public static class DefaultableConfig {
    public int count = 42;
  }

  /**
   * Mutable POJO for {@link ExporterConfiguration.ConfigurationMapper} tests. Records are
   * immutable, so a plain class is required to exercise {@code apply()}.
   */
  static class MutableConfig {
    public int count;
  }
}
