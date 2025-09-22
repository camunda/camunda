/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsciiEnumParserPropertyTest {

  private AsciiEnumParser<TestEnum> parser;
  private AsciiEnumParser<SingleEnum> singleParser;
  private AsciiEnumParser<EmptyLikeEnum> minimalParser;

  @BeforeEach
  @BeforeProperty
  void setUp() {
    parser = new AsciiEnumParser<>(TestEnum.class);
    singleParser = new AsciiEnumParser<>(SingleEnum.class);
    minimalParser = new AsciiEnumParser<>(EmptyLikeEnum.class);
  }

  @Property
  void shouldParseAllValidEnumValues(@ForAll("validEnumValue") final TestEnum enumValue) {
    // Arrange
    final String enumName = enumValue.name();
    final byte[] bytes = enumName.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length);

    // Assert
    Assertions.assertThat(result).isEqualTo(enumValue);
  }

  @Property
  void shouldParseValidEnumWithOffset(
      @ForAll("validEnumValue") final TestEnum enumValue,
      @ForAll @IntRange(min = 0, max = 20) final int prefixLength,
      @ForAll @IntRange(min = 0, max = 20) final int suffixLength) {
    // Arrange
    final String enumName = enumValue.name();
    final String prefix = "x".repeat(prefixLength);
    final String suffix = "y".repeat(suffixLength);
    final String fullString = prefix + enumName + suffix;

    final byte[] bytes = fullString.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, prefixLength, enumName.length());

    // Assert
    Assertions.assertThat(result).isEqualTo(enumValue);
  }

  @Property
  void shouldNotParseValidEnumWithOffsetWithInvalidLength(
      @ForAll("validEnumValue") final TestEnum enumValue,
      @ForAll @IntRange(min = 0, max = 20) final int prefixLength,
      @ForAll @IntRange(min = 0, max = 20) final int suffixLength) {
    // Arrange
    final String enumName = enumValue.name();
    final String prefix = "x".repeat(prefixLength);
    final String suffix = "y".repeat(suffixLength);
    final String fullString = prefix + enumName + suffix;

    final byte[] bytes = fullString.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, prefixLength, enumName.length() - 1);

    // Assert
    Assertions.assertThat(result).isNull();
  }

  @Property
  void shouldReturnNullForInvalidEnumNames(@ForAll("invalidEnumString") final String invalidName) {
    // Arrange
    final byte[] bytes = invalidName.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length);

    // Assert
    Assertions.assertThat(result).isNull();
  }

  @Property
  void shouldReturnNullForPartialMatches(
      @ForAll("validEnumValue") final TestEnum enumValue,
      @ForAll @IntRange(min = 1, max = 10) final int truncateBy) {
    // Arrange
    final String enumName = enumValue.name();
    if (enumName.length() <= truncateBy) {
      return; // Skip if truncation would result in empty string
    }

    final String truncated = enumName.substring(0, enumName.length() - truncateBy);
    final byte[] bytes = truncated.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length);

    // Assert - should be null unless truncated string happens to be another valid enum
    if (result != null) {
      Assertions.assertThat(result.name()).isEqualTo(truncated);
    }
  }

  @Property
  void shouldReturnNullForExtraCharacters(
      @ForAll("validEnumValue") final TestEnum enumValue,
      @ForAll("asciiString") @StringLength(min = 1, max = 5) final String suffix) {
    // Arrange
    final String enumName = enumValue.name();
    final String extended = enumName + suffix;
    final byte[] bytes = extended.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length);

    // Assert - should be null unless extended string happens to be another valid enum
    if (result != null) {
      Assertions.assertThat(result.name()).isEqualTo(extended);
    }
  }

  @Property
  void shouldHandleZeroLength() {
    // Arrange
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[10]);

    // Act
    final TestEnum result = parser.parse(buffer, 0, 0);

    // Assert
    Assertions.assertThat(result).isNull();
  }

  @Property
  void shouldReturnDefaultValueWhenSpecified(
      @ForAll("validEnumValue") final TestEnum defaultValue,
      @ForAll("invalidEnumString") final String invalidName) {
    // Arrange
    final byte[] bytes = invalidName.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length, defaultValue);

    // Assert
    Assertions.assertThat(result).isEqualTo(defaultValue);
  }

  @Property
  void shouldNotReturnDefaultValueForValidEnum(
      @ForAll("validEnumValue") final TestEnum enumValue,
      @ForAll("validEnumValue") final TestEnum defaultValue) {
    // Arrange
    final String enumName = enumValue.name();
    final byte[] bytes = enumName.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length, defaultValue);

    // Assert
    Assertions.assertThat(result).isEqualTo(enumValue);
  }

  @Property
  void shouldHandleNonAsciiCharacters(@ForAll("nonAsciiString") final String nonAsciiString) {
    // Arrange
    final byte[] bytes = nonAsciiString.getBytes(StandardCharsets.UTF_8);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final TestEnum result = parser.parse(buffer, 0, bytes.length);

    // Assert
    Assertions.assertThat(result).isNull();
  }

  @Test
  void shouldWorkWithSingleEnumValue() {
    // Arrange
    final String enumName = "ONLY_ONE";
    final byte[] bytes = enumName.getBytes(StandardCharsets.US_ASCII);
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    // Act
    final SingleEnum result = singleParser.parse(buffer, 0, bytes.length);

    // Assert
    Assertions.assertThat(result).isEqualTo(SingleEnum.ONLY_ONE);
  }

  // Providers for test data generation
  @Provide
  Arbitrary<TestEnum> validEnumValue() {
    return Arbitraries.of(TestEnum.class);
  }

  @Provide
  Arbitrary<String> invalidEnumString() {
    return Arbitraries.oneOf(
        // Random ASCII strings that are unlikely to match enum names
        Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(20)
            .filter(s -> !isValidEnumName(s)),

        // Numbers
        Arbitraries.strings().withCharRange('0', '9').ofMinLength(1).ofMaxLength(10),

        // Special characters
        Arbitraries.strings()
            .withChars("!@#$%^&*()[]{}|\\:;\"'<>?,./")
            .ofMinLength(1)
            .ofMaxLength(5),

        // Mixed invalid strings
        Arbitraries.strings()
            .ascii()
            .ofMinLength(1)
            .ofMaxLength(15)
            .filter(s -> !isValidEnumName(s) && isAsciiPrintable(s)));
  }

  @Provide
  Arbitrary<String> asciiString() {
    return Arbitraries.strings()
        .withCharRange((char) 32, (char) 126) // ASCII printable range
        .ofMinLength(0)
        .ofMaxLength(20);
  }

  @Provide
  Arbitrary<String> nonAsciiString() {
    return Arbitraries.strings()
        .withCharRange((char) 128, (char) 255) // Non-ASCII characters
        .ofMinLength(1)
        .ofMaxLength(10);
  }

  // Helper methods
  private boolean isValidEnumName(final String name) {
    return Arrays.stream(TestEnum.values())
        .map(Enum::name)
        .anyMatch(enumName -> enumName.equals(name));
  }

  private boolean isAsciiPrintable(final String s) {
    return s.chars().allMatch(c -> c >= 32 && c <= 126);
  }

  // Test enum with various ASCII characters
  enum TestEnum {
    ALPHA,
    BETA,
    GAMMA,
    DELTA,
    EPSILON,
    LONG_ENUM_NAME_WITH_UNDERSCORES,
    ABC123,
    DEF456,
    A,
    B,
    C, // Single character names
    MIXED_case_Name, // Mixed case (though enum conventions discourage this)
    SPECIAL_CHARS_123
  }

  // Another enum for edge cases
  enum SingleEnum {
    ONLY_ONE
  }

  enum EmptyLikeEnum {
    // Intentionally minimal
    X
  }
}
