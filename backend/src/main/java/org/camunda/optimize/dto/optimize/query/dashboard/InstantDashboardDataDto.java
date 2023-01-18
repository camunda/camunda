/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class InstantDashboardDataDto {
  private String id;
  private String processDefinitionKey;
  private String templateName = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
  private String templateHash;
  private String dashboardId;

  public static final String INSTANT_DASHBOARD_DEFAULT_TEMPLATE = "default.json";

  public String getId() {
    if(templateName == null || templateName.isEmpty()) {
      templateName = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
    }
    // remove any dots from the name
    return processDefinitionKey + "_" + templateName.replaceAll("\\.","");
  }
}
