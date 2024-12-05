/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import java.util.Map;

public record Metadata(Long backupId, String version, Integer partNo, Integer partCount) {

  // used by open search backend
  public Map<String, co.elastic.clients.json.JsonData> asJsonES(
      final co.elastic.clients.json.JsonpMapper jsonpMapper) {
    return Map.of(
        "backupId", co.elastic.clients.json.JsonData.of(backupId(), jsonpMapper),
        "version", co.elastic.clients.json.JsonData.of(version(), jsonpMapper),
        "partNo", co.elastic.clients.json.JsonData.of(partNo(), jsonpMapper),
        "partCount", co.elastic.clients.json.JsonData.of(partCount(), jsonpMapper));
  }

  public static Metadata fromMetadataES(
      final Map<String, co.elastic.clients.json.JsonData> metadata,
      final co.elastic.clients.json.JsonpMapper jsonpMapper) {
    try {
      final var backupId = metadata.get("backupId").to(Long.class, jsonpMapper);
      final var version = metadata.get("version").to(String.class, jsonpMapper);
      final var partNo = metadata.get("partNo").to(Integer.class, jsonpMapper);
      final var partCount = metadata.get("partCount").to(Integer.class, jsonpMapper);
      return new Metadata(backupId, version, partNo, partCount);
    } catch (final Exception e) {
      throw new RuntimeException("Unable to deserialize metadata %s".formatted(metadata), e);
    }
  }

  // used by open search backend
  public Map<String, org.opensearch.client.json.JsonData> asJson(
      final org.opensearch.client.json.JsonpMapper jsonpMapper) {
    return Map.of(
        "backupId", org.opensearch.client.json.JsonData.of(backupId(), jsonpMapper),
        "version", org.opensearch.client.json.JsonData.of(version(), jsonpMapper),
        "partNo", org.opensearch.client.json.JsonData.of(partNo(), jsonpMapper),
        "partCount", org.opensearch.client.json.JsonData.of(partCount(), jsonpMapper));
  }

  public static Metadata fromMetadata(
      final Map<String, org.opensearch.client.json.JsonData> metadata,
      final org.opensearch.client.json.JsonpMapper jsonpMapper) {
    try {
      final var backupId = metadata.get("backupId").to(Long.class, jsonpMapper);
      final var version = metadata.get("version").to(String.class, jsonpMapper);
      final var partNo = metadata.get("partNo").to(Integer.class, jsonpMapper);
      final var partCount = metadata.get("partCount").to(Integer.class, jsonpMapper);
      return new Metadata(backupId, version, partNo, partCount);
    } catch (final Exception e) {
      throw new RuntimeException("Unable to deserialize metadata %s".formatted(metadata), e);
    }
  }
}
