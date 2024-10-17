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
    final int PRIME = 59;
    int result = 1;
    final Object $authorizedUserType = getAuthorizedUserType();
    result = result * PRIME + ($authorizedUserType == null ? 43 : $authorizedUserType.hashCode());
    final Object $kpiRefreshInterval = getKpiRefreshInterval();
    result = result * PRIME + ($kpiRefreshInterval == null ? 43 : $kpiRefreshInterval.hashCode());
    final Object $createOnStartup = getCreateOnStartup();
    result = result * PRIME + ($createOnStartup == null ? 43 : $createOnStartup.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityConfiguration)) {
      return false;
    }
    final EntityConfiguration other = (EntityConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$authorizedUserType = getAuthorizedUserType();
    final Object other$authorizedUserType = other.getAuthorizedUserType();
    if (this$authorizedUserType == null
        ? other$authorizedUserType != null
        : !this$authorizedUserType.equals(other$authorizedUserType)) {
      return false;
    }
    final Object this$kpiRefreshInterval = getKpiRefreshInterval();
    final Object other$kpiRefreshInterval = other.getKpiRefreshInterval();
    if (this$kpiRefreshInterval == null
        ? other$kpiRefreshInterval != null
        : !this$kpiRefreshInterval.equals(other$kpiRefreshInterval)) {
      return false;
    }
    final Object this$createOnStartup = getCreateOnStartup();
    final Object other$createOnStartup = other.getCreateOnStartup();
    if (this$createOnStartup == null
        ? other$createOnStartup != null
        : !this$createOnStartup.equals(other$createOnStartup)) {
      return false;
    }
    return true;
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
