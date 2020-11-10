/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

  protected String name;
  protected AlertInterval checkInterval;
  protected String reportId;
  protected Double threshold;
  protected AlertThresholdOperator thresholdOperator;
  protected boolean fixNotification;
  protected AlertInterval reminder;
  protected List<String> emails = new ArrayList<>();
  protected String webhook;
}
