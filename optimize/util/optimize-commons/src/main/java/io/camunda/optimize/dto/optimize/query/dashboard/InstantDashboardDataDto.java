/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@FieldNameConstants
public class InstantDashboardDataDto {
  private String instantDashboardId;
  private String processDefinitionKey;
  private String templateName = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
  private long templateHash;
  private String dashboardId;

  public static final String INSTANT_DASHBOARD_DEFAULT_TEMPLATE = "template1.json";

  public String getInstantDashboardId() {
    return processDefinitionKey + "_" + templateName.replace(".", "");
  }

  public void setTemplateName(String templateName) {
    this.templateName =
        StringUtils.isEmpty(templateName) ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : templateName;
  }
}
