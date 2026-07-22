/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import java.util.Map;

/**
 * SPIKE (ADR-0038): persisted representation of a CSL web session in the {@code web-session} index.
 * Mirrors CSL's {@code PersistentSession}. Jackson serializes each {@code byte[]} attribute value
 * as Base64, so {@code attributes} round-trips as a JSON object stored in the (non-indexed)
 * attributes field. A no-arg constructor is required for deserialization.
 */
public class WebSessionDto {

  private String id;
  private Long creationTime;
  private Long lastAccessedTime;
  private Long maxInactiveIntervalInSeconds;
  private Map<String, byte[]> attributes;

  public WebSessionDto() {}

  public WebSessionDto(
      final String id,
      final Long creationTime,
      final Long lastAccessedTime,
      final Long maxInactiveIntervalInSeconds,
      final Map<String, byte[]> attributes) {
    this.id = id;
    this.creationTime = creationTime;
    this.lastAccessedTime = lastAccessedTime;
    this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
    this.attributes = attributes;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Long getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(final Long creationTime) {
    this.creationTime = creationTime;
  }

  public Long getLastAccessedTime() {
    return lastAccessedTime;
  }

  public void setLastAccessedTime(final Long lastAccessedTime) {
    this.lastAccessedTime = lastAccessedTime;
  }

  public Long getMaxInactiveIntervalInSeconds() {
    return maxInactiveIntervalInSeconds;
  }

  public void setMaxInactiveIntervalInSeconds(final Long maxInactiveIntervalInSeconds) {
    this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
  }

  public Map<String, byte[]> getAttributes() {
    return attributes;
  }

  public void setAttributes(final Map<String, byte[]> attributes) {
    this.attributes = attributes;
  }
}
