/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.cloud.panelnotifications;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
}
