/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A log object as it appears in the bucket: one {@code data/*.rec} file and its index range. Used
 * in commit entries, index chunks and roots.
 *
 * @param key object key relative to the partition prefix, e.g. {@code data/...-t7.rec}
 * @param firstPosition {@code -1} if the object holds no application entry
 * @param lastPosition {@code -1} if the object holds no application entry
 * @param sha256 lowercase hex SHA-256 of the whole object
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
  "sha256"
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
    String sha256) {}
