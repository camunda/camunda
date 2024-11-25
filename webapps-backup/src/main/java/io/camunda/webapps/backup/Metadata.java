/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;

public record Metadata(Long backupId, String version, Integer partNo, Integer partCount) {

  // used by open search backend
  public Map<String, JsonData> asJson(final JsonpMapper jsonpMapper) {
    return Map.of(
        "backupId", JsonData.of(backupId(), jsonpMapper),
        "version", JsonData.of(version(), jsonpMapper),
        "partNo", JsonData.of(partNo(), jsonpMapper),
        "partCount", JsonData.of(partCount(), jsonpMapper));
  }

  public static Metadata fromMetadata(
      final Map<String, JsonData> metadata, final JsonpMapper jsonpMapper) {
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
