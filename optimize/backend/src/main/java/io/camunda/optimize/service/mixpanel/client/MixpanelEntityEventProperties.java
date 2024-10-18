/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MixpanelEntityEventProperties extends MixpanelEventProperties {

  @JsonProperty("entityId")
  private String entityId;

  public MixpanelEntityEventProperties(
      final String entityId,
      final String stage,
      final String organizationId,
      final String clusterId) {
    super(stage, organizationId, clusterId);
    this.entityId = entityId;
  }

  public String getEntityId() {
    return entityId;
  }

  @JsonProperty("entityId")
  public void setEntityId(final String entityId) {
    this.entityId = entityId;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelEntityEventProperties;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "MixpanelEntityEventProperties(entityId=" + getEntityId() + ")";
  }
}
