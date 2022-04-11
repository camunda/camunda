/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MixpanelEntityEventProperties extends MixpanelEventProperties {
  @JsonProperty("entityId")
  private String entityId;

  public MixpanelEntityEventProperties(final String entityId,
                                       final String stage,
                                       final String organizationId,
                                       final String clusterId) {
    super(stage, organizationId, clusterId);
    this.entityId = entityId;
  }
}
