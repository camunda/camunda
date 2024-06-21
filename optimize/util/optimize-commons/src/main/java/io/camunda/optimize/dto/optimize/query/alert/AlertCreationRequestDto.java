/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class AlertCreationRequestDto {

  private String name;
  private AlertInterval checkInterval;
  private String reportId;
  private Double threshold;
  private AlertThresholdOperator thresholdOperator;
  private boolean fixNotification;
  private AlertInterval reminder;
  private final List<String> emails = new ArrayList<>();
  private String webhook;

  // needed to allow inheritance of field name constants
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Fields {}
}
