/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.alert;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.List;

@Data
@FieldNameConstants
public class AlertCreationRequestDto {

  // needed to allow inheritance of field name constants
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Fields {}

  private String name;
  private AlertInterval checkInterval;
  private String reportId;
  private Double threshold;
  private AlertThresholdOperator thresholdOperator;
  private boolean fixNotification;
  private AlertInterval reminder;
  private List<String> emails = new ArrayList<>();
  private String webhook;
}
