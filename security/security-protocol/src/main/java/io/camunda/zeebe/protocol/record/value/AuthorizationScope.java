/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import java.util.Objects;

public class AuthorizationScope {

  private ResourceIdFormat format;
  private String value;

  public AuthorizationScope() {}

  public AuthorizationScope(final ResourceIdFormat format, final String value) {
    this.format = format;
    this.value = value;
  }

  public ResourceIdFormat getFormat() {
    return format;
  }

  public void setFormat(final ResourceIdFormat format) {
    this.format = format;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public static AuthorizationScope of(final ResourceIdFormat format, final String value) {
    return new AuthorizationScope(format, value);
  }

  public static AuthorizationScope wildcard() {
    return new AuthorizationScope(ResourceIdFormat.ANY, null);
  }

  public static ResourceIdFormat getFormatOf(final String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) {
      return ResourceIdFormat.UNSPECIFIED;
    }
    if ("*".equals(resourceId)) {
      return ResourceIdFormat.ANY;
    }
    return ResourceIdFormat.ID;
  }

  @Override
  public int hashCode() {
    return Objects.hash(format, value);
  }

  @Override
  public boolean equals(final Object obj) {
    final AuthorizationScope other = (AuthorizationScope) obj;
    if (format.equals(other.format) && value.equals(other.value)) {
      return true;
    }
    return false;
  }
}
