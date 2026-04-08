/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.policy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Utility policy operations shared by contract adapters. */
@NullMarked
public final class ContractPolicy {

  private ContractPolicy() {}

  public static ScopedPolicy scoped(final String contractName) {
    return new ScopedPolicy(contractName);
  }

  public static FieldRef field(final String contractName, final String fieldName) {
    return new FieldRef(contractName, fieldName);
  }

  public static <T> FieldPolicy<T> requiredNonNull() {
    return (value, field, sourceEntity) -> requiredNonNull(value, field, sourceEntity);
  }

  public static <T> FieldPolicy<T> defaultIfNull(final T defaultValue) {
    return (value, field, sourceEntity) -> value == null ? defaultValue : value;
  }

  /**
   * Requires a value to be non-null. Accepts a {@code @Nullable} value and returns a guaranteed
   * non-null result, or throws with a descriptive message identifying the contract field.
   */
  public static <T> T requireNonNull(
      final @Nullable T value, final ContractPolicy.FieldRef field, final Object sourceEntity) {
    return requiredNonNull(value, field, sourceEntity);
  }

  public static <T> T requiredNonNull(
      final @Nullable T value,
      final String contractName,
      final String fieldName,
      final Object sourceEntity) {
    return Objects.requireNonNull(
        value,
        () ->
            contractName
                + " contract field '"
                + fieldName
                + "' is required and non-nullable, but source provided null: "
                + sourceEntity);
  }

  public static <T> T requiredNonNull(
      final @Nullable T value, final FieldRef field, final Object sourceEntity) {
    return requiredNonNull(value, field.contractName(), field.fieldName(), sourceEntity);
  }

  public static boolean isNotBlank(final String value) {
    return StringUtils.isNotBlank(value);
  }

  public static @Nullable String blankToNull(final @Nullable String value) {
    return StringUtils.defaultIfBlank(value, null);
  }

  public static <T> List<T> nullToEmptyList(final @Nullable List<T> values) {
    return values == null ? List.of() : values;
  }

  public static <K, V> Map<K, V> nullToEmptyMap(final @Nullable Map<K, V> values) {
    return values == null ? Map.of() : values;
  }

  public static <T> T defaultIfNull(final @Nullable T value, final T defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * Null-safe enum-to-protocol-enum coercion. Returns {@code null} when the source enum is {@code
   * null}; otherwise maps via {@code source.name()} through the given {@code fromValue} function.
   */
  public static <S extends Enum<S>, T> @Nullable T mapEnum(
      final @Nullable S source, final Function<String, T> fromValue) {
    return source != null ? fromValue.apply(source.name()) : null;
  }

  /**
   * Resolves a preview/full value pair. Returns {@code fullValue} when the entity is a preview;
   * otherwise returns {@code value}.
   */
  public static String resolvePreviewValue(
      final String value, final String fullValue, final boolean isPreview) {
    return isPreview ? fullValue : value;
  }

  /** Scoped policy with contract name pre-bound once, to avoid repeating it at each call site. */
  public static final class ScopedPolicy {
    private final String contractName;

    private ScopedPolicy(final String contractName) {
      this.contractName = Objects.requireNonNull(contractName, "contractName must not be null");
    }

    public <T> T requiredNonNull(
        final @Nullable T value, final String fieldName, final Object sourceEntity) {
      return ContractPolicy.requiredNonNull(value, contractName, fieldName, sourceEntity);
    }

    public <T> T requiredNonNull(
        final @Nullable T value, final FieldRef field, final Object sourceEntity) {
      return ContractPolicy.requiredNonNull(value, field, sourceEntity);
    }
  }

  public record FieldRef(String contractName, String fieldName) {
    public FieldRef {
      Objects.requireNonNull(contractName, "contractName must not be null");
      Objects.requireNonNull(fieldName, "fieldName must not be null");
    }
  }

  @FunctionalInterface
  public interface FieldPolicy<T extends @Nullable Object> {
    T apply(T value, FieldRef field, Object sourceEntity);
  }
}
