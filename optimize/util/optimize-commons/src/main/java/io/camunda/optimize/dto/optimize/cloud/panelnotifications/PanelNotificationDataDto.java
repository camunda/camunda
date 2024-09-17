/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Builder
@Data
public class PanelNotificationDataDto {

  private final String uniqueId;
  private final String source;
  private final String type;
  private final String orgId;
  private final String title;
  private final String description;
  private final PanelNotificationMetaDataDto meta;

  private PanelNotificationDataDto(
      String uniqueId,
      String source,
      String type,
      String orgId,
      String title,
      String description,
      PanelNotificationMetaDataDto meta) {
    this.uniqueId = uniqueId;
    this.source = source;
    this.type = type;
    this.orgId = orgId;
    this.title = title;
    this.description = description;
    this.meta = meta;
  }
}
