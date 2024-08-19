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
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $entityId = getEntityId();
    result = result * PRIME + ($entityId == null ? 43 : $entityId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelEntityEventProperties)) {
      return false;
    }
    final MixpanelEntityEventProperties other = (MixpanelEntityEventProperties) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$entityId = getEntityId();
    final Object other$entityId = other.getEntityId();
    if (this$entityId == null ? other$entityId != null : !this$entityId.equals(other$entityId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MixpanelEntityEventProperties(entityId=" + getEntityId() + ")";
  }
}
