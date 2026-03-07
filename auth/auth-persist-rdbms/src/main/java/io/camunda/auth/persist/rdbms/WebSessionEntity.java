/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import java.util.Map;

/** Entity mapping for the AUTH_WEB_SESSION table. */
public class WebSessionEntity {

  private String sessionId;
  private long creationTime;
  private long lastAccessedTime;
  private long maxInactiveIntervalInSeconds;
  private Map<String, byte[]> attributes;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public long getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(final long creationTime) {
    this.creationTime = creationTime;
  }

  public long getLastAccessedTime() {
    return lastAccessedTime;
  }

  public void setLastAccessedTime(final long lastAccessedTime) {
    this.lastAccessedTime = lastAccessedTime;
  }

  public long getMaxInactiveIntervalInSeconds() {
    return maxInactiveIntervalInSeconds;
  }

  public void setMaxInactiveIntervalInSeconds(final long maxInactiveIntervalInSeconds) {
    this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
  }

  public Map<String, byte[]> getAttributes() {
    return attributes;
  }

  public void setAttributes(final Map<String, byte[]> attributes) {
    this.attributes = attributes;
  }
}
