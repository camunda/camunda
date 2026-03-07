/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import io.camunda.auth.domain.model.SessionData;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Document representation for Elasticsearch storage of web session data.
 *
 * @param id unique session identifier
 * @param creationTime when the session was created (epoch millis)
 * @param lastAccessedTime when the session was last accessed (epoch millis)
 * @param maxInactiveIntervalInSeconds maximum idle time before the session expires
 * @param attributes session attributes stored as Base64-encoded strings
 */
public record ElasticsearchSessionDocument(
    String id,
    long creationTime,
    long lastAccessedTime,
    long maxInactiveIntervalInSeconds,
    Map<String, String> attributes) {

  /**
   * Creates an Elasticsearch document from a domain {@link SessionData} instance. The byte-array
   * attribute values are Base64-encoded for safe storage in Elasticsearch.
   *
   * @param data the domain session data
   * @return the Elasticsearch document representation
   */
  public static ElasticsearchSessionDocument fromDomain(final SessionData data) {
    Map<String, String> encoded = Map.of();
    if (data.attributes() != null && !data.attributes().isEmpty()) {
      encoded =
          data.attributes().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      e -> Base64.getEncoder().encodeToString(e.getValue())));
    }
    return new ElasticsearchSessionDocument(
        data.id(),
        data.creationTime(),
        data.lastAccessedTime(),
        data.maxInactiveIntervalInSeconds(),
        encoded);
  }

  /**
   * Converts this Elasticsearch document back to a domain {@link SessionData} instance. The
   * Base64-encoded attribute values are decoded back to byte arrays.
   *
   * @return the domain session data
   */
  public SessionData toDomain() {
    final Map<String, byte[]> decoded = new HashMap<>();
    if (attributes != null) {
      attributes.forEach((k, v) -> decoded.put(k, Base64.getDecoder().decode(v)));
    }
    return new SessionData(
        id, creationTime, lastAccessedTime, maxInactiveIntervalInSeconds, decoded);
  }
}
