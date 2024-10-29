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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String instantDashboardId = "instantDashboardId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String templateName = "templateName";
    public static final String templateHash = "templateHash";
    public static final String dashboardId = "dashboardId";
  }
}
