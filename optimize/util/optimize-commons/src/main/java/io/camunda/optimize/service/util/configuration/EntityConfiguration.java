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
import java.util.Objects;

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
    return Objects.hash(authorizedUserType, kpiRefreshInterval, createOnStartup);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EntityConfiguration that = (EntityConfiguration) o;
    return Objects.equals(authorizedUserType, that.authorizedUserType)
        && Objects.equals(kpiRefreshInterval, that.kpiRefreshInterval)
        && Objects.equals(createOnStartup, that.createOnStartup);
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
