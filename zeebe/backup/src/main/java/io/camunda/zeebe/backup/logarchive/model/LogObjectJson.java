/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * A log object as it appears in the bucket: one {@code data/*.seg} (or, before segment format v1,
 * {@code data/*.rec}) file and its index range. Used in commit entries, index chunks and roots.
 *
 * @param key object key relative to the partition prefix, e.g. {@code data/...-t7.rec}
 * @param firstPosition {@code -1} if the object holds no application entry
 * @param lastPosition {@code -1} if the object holds no application entry
 * @param sha256 lowercase hex SHA-256 of the whole object
 * @param bodyLength length of the body of a {@code .seg} object, which is where its metadata
 *     starts; -1 for a {@code .rec} object, in which case the JSON omits it
 * @param lastRecordTimestamp highest record timestamp of the application entries in epoch millis;
 *     -1 if unknown or the object holds none, in which case the JSON omits it
 */
@JsonPropertyOrder({
  "key",
  "term",
  "firstIndex",
  "lastIndex",
  "firstPosition",
  "lastPosition",
  "entryCount",
  "sizeBytes",
  "sha256",
  "bodyLength",
  "lastRecordTimestamp"
})
public record LogObjectJson(
    String key,
    long term,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    int entryCount,
    long sizeBytes,
    String sha256,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NoValue.class) long bodyLength,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NoValue.class)
        long lastRecordTimestamp) {

  public LogObjectJson(
      final String key,
      final long term,
      final long firstIndex,
      final long lastIndex,
      final long firstPosition,
      final long lastPosition,
      final int entryCount,
      final long sizeBytes,
      final String sha256,
      final long bodyLength) {
    this(
        key,
        term,
        firstIndex,
        lastIndex,
        firstPosition,
        lastPosition,
        entryCount,
        sizeBytes,
        sha256,
        bodyLength,
        -1L);
  }

  public LogObjectJson(
      final String key,
      final long term,
      final long firstIndex,
      final long lastIndex,
      final long firstPosition,
      final long lastPosition,
      final int entryCount,
      final long sizeBytes,
      final String sha256) {
    this(
        key,
        term,
        firstIndex,
        lastIndex,
        firstPosition,
        lastPosition,
        entryCount,
        sizeBytes,
        sha256,
        -1L,
        -1L);
  }

  /**
   * Reads a missing body length or last record timestamp as -1, which a record's canonical
   * constructor cannot express.
   */
  @JsonCreator
  static LogObjectJson fromJson(
      @JsonProperty("key") final String key,
      @JsonProperty("term") final long term,
      @JsonProperty("firstIndex") final long firstIndex,
      @JsonProperty("lastIndex") final long lastIndex,
      @JsonProperty("firstPosition") final long firstPosition,
      @JsonProperty("lastPosition") final long lastPosition,
      @JsonProperty("entryCount") final int entryCount,
      @JsonProperty("sizeBytes") final long sizeBytes,
      @JsonProperty("sha256") final String sha256,
      @JsonProperty("bodyLength") final @Nullable Long bodyLength,
      @JsonProperty("lastRecordTimestamp") final @Nullable Long lastRecordTimestamp) {
    return new LogObjectJson(
        key,
        term,
        firstIndex,
        lastIndex,
        firstPosition,
        lastPosition,
        entryCount,
        sizeBytes,
        sha256,
        bodyLength == null ? -1L : bodyLength,
        lastRecordTimestamp == null ? -1L : lastRecordTimestamp);
  }

  /** Jackson value filter: a value of -1 is not written. */
  static final class NoValue {
    @Override
    public boolean equals(final @Nullable Object value) {
      return value instanceof final Long l && l == -1L;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}
