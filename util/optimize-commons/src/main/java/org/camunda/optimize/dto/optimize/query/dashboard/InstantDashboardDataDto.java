/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

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
    return processDefinitionKey + "_" + templateName.replace(".","");
  }

  public void setTemplateName(String templateName) {
    this.templateName = StringUtils.isEmpty(templateName)
      ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : templateName;
  }
}
