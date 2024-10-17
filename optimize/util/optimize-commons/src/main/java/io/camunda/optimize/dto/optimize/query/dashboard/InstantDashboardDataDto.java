/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstantDashboardDataDto {

  public static final String INSTANT_DASHBOARD_DEFAULT_TEMPLATE = "template1.json";
  private String instantDashboardId;
  private String processDefinitionKey;
  private String templateName = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
  private long templateHash;
  private String dashboardId;

  public InstantDashboardDataDto() {}

  public String getInstantDashboardId() {
    return processDefinitionKey + "_" + templateName.replace(".", "");
  }

  public void setInstantDashboardId(final String instantDashboardId) {
    this.instantDashboardId = instantDashboardId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getTemplateName() {
    return templateName;
  }

  public void setTemplateName(final String templateName) {
    this.templateName =
        StringUtils.isEmpty(templateName) ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : templateName;
  }

  public long getTemplateHash() {
    return templateHash;
  }

  public void setTemplateHash(final long templateHash) {
    this.templateHash = templateHash;
  }

  public String getDashboardId() {
    return dashboardId;
  }

  public void setDashboardId(final String dashboardId) {
    this.dashboardId = dashboardId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof InstantDashboardDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $instantDashboardId = getInstantDashboardId();
    result = result * PRIME + ($instantDashboardId == null ? 43 : $instantDashboardId.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $templateName = getTemplateName();
    result = result * PRIME + ($templateName == null ? 43 : $templateName.hashCode());
    final long $templateHash = getTemplateHash();
    result = result * PRIME + (int) ($templateHash >>> 32 ^ $templateHash);
    final Object $dashboardId = getDashboardId();
    result = result * PRIME + ($dashboardId == null ? 43 : $dashboardId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof InstantDashboardDataDto)) {
      return false;
    }
    final InstantDashboardDataDto other = (InstantDashboardDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$instantDashboardId = getInstantDashboardId();
    final Object other$instantDashboardId = other.getInstantDashboardId();
    if (this$instantDashboardId == null
        ? other$instantDashboardId != null
        : !this$instantDashboardId.equals(other$instantDashboardId)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$templateName = getTemplateName();
    final Object other$templateName = other.getTemplateName();
    if (this$templateName == null
        ? other$templateName != null
        : !this$templateName.equals(other$templateName)) {
      return false;
    }
    if (getTemplateHash() != other.getTemplateHash()) {
      return false;
    }
    final Object this$dashboardId = getDashboardId();
    final Object other$dashboardId = other.getDashboardId();
    if (this$dashboardId == null
        ? other$dashboardId != null
        : !this$dashboardId.equals(other$dashboardId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "InstantDashboardDataDto(instantDashboardId="
        + getInstantDashboardId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", templateName="
        + getTemplateName()
        + ", templateHash="
        + getTemplateHash()
        + ", dashboardId="
        + getDashboardId()
        + ")";
  }

  public static final class Fields {

    public static final String instantDashboardId = "instantDashboardId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String templateName = "templateName";
    public static final String templateHash = "templateHash";
    public static final String dashboardId = "dashboardId";
  }
}
