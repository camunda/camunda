/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

/**
 * Be aware that all configuration which are part of this class are experimental, which means they
 * are subject to change and to drop. It might be that also some of them are actually dangerous so
 * be aware when you change one of these!
 */
public class ExperimentalCfg {

  private IdentityServiceCfg identityRequest = new IdentityServiceCfg();

  public IdentityServiceCfg getIdentityRequest() {
    return identityRequest;
  }

  public void setIdentityRequest(final IdentityServiceCfg identityRequest) {
    this.identityRequest = identityRequest;
  }

  @Override
  public int hashCode() {
    return Objects.hash(identityRequest);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExperimentalCfg that = (ExperimentalCfg) o;
    return Objects.equals(identityRequest, that.identityRequest);
  }

  @Override
  public String toString() {
    return "ExperimentalCfg{" + "identityRequest=" + identityRequest + '}';
  }
}
