/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;

public class EntityConfiguration {

  @JsonProperty("authorizedEditors")
  private AuthorizedUserType authorizedUserType;

  private Long kpiRefreshInterval;

  private Boolean createOnStartup;

  public EntityConfiguration() {}

  public AuthorizedUserType getAuthorizedUserType() {
    return authorizedUserType;
  }

  @JsonProperty("authorizedEditors")
  public void setAuthorizedUserType(final AuthorizedUserType authorizedUserType) {
    this.authorizedUserType = authorizedUserType;
  }

  public Long getKpiRefreshInterval() {
    return kpiRefreshInterval;
  }

  public void setKpiRefreshInterval(final Long kpiRefreshInterval) {
    this.kpiRefreshInterval = kpiRefreshInterval;
  }

  public Boolean getCreateOnStartup() {
    return createOnStartup;
  }

  public void setCreateOnStartup(final Boolean createOnStartup) {
    this.createOnStartup = createOnStartup;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityConfiguration;
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
    return "EntityConfiguration(authorizedUserType="
        + getAuthorizedUserType()
        + ", kpiRefreshInterval="
        + getKpiRefreshInterval()
        + ", createOnStartup="
        + getCreateOnStartup()
        + ")";
  }
}
