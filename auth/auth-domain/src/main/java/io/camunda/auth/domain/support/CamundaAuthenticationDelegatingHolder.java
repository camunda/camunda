/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.support;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationHolder;
import java.util.List;

/**
 * Delegating {@link CamundaAuthenticationHolder} responsible to delegate to a matching {@link
 * CamundaAuthenticationHolder} to set and get a {@link CamundaAuthentication}. If no matching
 * {@link CamundaAuthenticationHolder} is found, it results in a noop, and no exception is thrown.
 */
public class CamundaAuthenticationDelegatingHolder implements CamundaAuthenticationHolder {

  private final List<CamundaAuthenticationHolder> holders;

  public CamundaAuthenticationDelegatingHolder(final List<CamundaAuthenticationHolder> holders) {
    this.holders = List.copyOf(holders);
  }

  @Override
  public boolean supports() {
    return holders.stream().anyMatch(CamundaAuthenticationHolder::supports);
  }

  @Override
  public void set(final CamundaAuthentication authentication) {
    holders.stream()
        .filter(CamundaAuthenticationHolder::supports)
        .findFirst()
        .ifPresent(holder -> holder.set(authentication));
  }

  @Override
  public CamundaAuthentication get() {
    return holders.stream()
        .filter(CamundaAuthenticationHolder::supports)
        .findFirst()
        .map(CamundaAuthenticationHolder::get)
        .orElse(null);
  }
}
