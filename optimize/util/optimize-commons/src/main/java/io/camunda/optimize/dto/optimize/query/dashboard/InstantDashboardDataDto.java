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
import org.apache.commons.lang3.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class InstantDashboardDataDto {

  public static final String INSTANT_DASHBOARD_DEFAULT_TEMPLATE = "template1.json";
  private String instantDashboardId;
  private String processDefinitionKey;
  private String templateName = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
  private long templateHash;
  private String dashboardId;

  public String getInstantDashboardId() {
    return processDefinitionKey + "_" + templateName.replace(".", "");
  }

  public void setTemplateName(final String templateName) {
    this.templateName =
        StringUtils.isEmpty(templateName) ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : templateName;
  }

  public static final class Fields {

    public static final String instantDashboardId = "instantDashboardId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String templateName = "templateName";
    public static final String templateHash = "templateHash";
    public static final String dashboardId = "dashboardId";
  }
}
