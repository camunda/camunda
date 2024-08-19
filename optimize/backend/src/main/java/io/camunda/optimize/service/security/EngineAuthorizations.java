/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.engine.AuthorizationDto;
import java.util.ArrayList;
import java.util.List;

public class EngineAuthorizations {

  private final String engine;
  private List<AuthorizationDto> globalAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> groupAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> userAuthorizations = new ArrayList<>();

  public EngineAuthorizations(final String engine) {
    this.engine = engine;
  }

  public EngineAuthorizations(
      final String engine,
      final List<AuthorizationDto> globalAuthorizations,
      final List<AuthorizationDto> groupAuthorizations,
      final List<AuthorizationDto> userAuthorizations) {
    this.engine = engine;
    this.globalAuthorizations = globalAuthorizations;
    this.groupAuthorizations = groupAuthorizations;
    this.userAuthorizations = userAuthorizations;
  }

  public String getEngine() {
    return engine;
  }

  public List<AuthorizationDto> getGlobalAuthorizations() {
    return globalAuthorizations;
  }

  public void setGlobalAuthorizations(final List<AuthorizationDto> globalAuthorizations) {
    this.globalAuthorizations = globalAuthorizations;
  }

  public List<AuthorizationDto> getGroupAuthorizations() {
    return groupAuthorizations;
  }

  public void setGroupAuthorizations(final List<AuthorizationDto> groupAuthorizations) {
    this.groupAuthorizations = groupAuthorizations;
  }

  public List<AuthorizationDto> getUserAuthorizations() {
    return userAuthorizations;
  }

  public void setUserAuthorizations(final List<AuthorizationDto> userAuthorizations) {
    this.userAuthorizations = userAuthorizations;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineAuthorizations;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $engine = getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    final Object $globalAuthorizations = getGlobalAuthorizations();
    result =
        result * PRIME + ($globalAuthorizations == null ? 43 : $globalAuthorizations.hashCode());
    final Object $groupAuthorizations = getGroupAuthorizations();
    result = result * PRIME + ($groupAuthorizations == null ? 43 : $groupAuthorizations.hashCode());
    final Object $userAuthorizations = getUserAuthorizations();
    result = result * PRIME + ($userAuthorizations == null ? 43 : $userAuthorizations.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineAuthorizations)) {
      return false;
    }
    final EngineAuthorizations other = (EngineAuthorizations) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$engine = getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    final Object this$globalAuthorizations = getGlobalAuthorizations();
    final Object other$globalAuthorizations = other.getGlobalAuthorizations();
    if (this$globalAuthorizations == null
        ? other$globalAuthorizations != null
        : !this$globalAuthorizations.equals(other$globalAuthorizations)) {
      return false;
    }
    final Object this$groupAuthorizations = getGroupAuthorizations();
    final Object other$groupAuthorizations = other.getGroupAuthorizations();
    if (this$groupAuthorizations == null
        ? other$groupAuthorizations != null
        : !this$groupAuthorizations.equals(other$groupAuthorizations)) {
      return false;
    }
    final Object this$userAuthorizations = getUserAuthorizations();
    final Object other$userAuthorizations = other.getUserAuthorizations();
    if (this$userAuthorizations == null
        ? other$userAuthorizations != null
        : !this$userAuthorizations.equals(other$userAuthorizations)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EngineAuthorizations(engine="
        + getEngine()
        + ", globalAuthorizations="
        + getGlobalAuthorizations()
        + ", groupAuthorizations="
        + getGroupAuthorizations()
        + ", userAuthorizations="
        + getUserAuthorizations()
        + ")";
  }
  ;
}
