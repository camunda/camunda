/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.holder;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import java.util.List;
import java.util.Optional;

/**
 * Delegating {@link CamundaAuthenticationHolder} responsible to delegate to a matching {@link
 * CamundaAuthenticationHolder} to set and get a {@link CamundaAuthentication}. If no matching
 * {@link CamundaAuthenticationHolder} is found, it results in a noop, and no exception is thrown.
 */
public class CamundaAuthenticationDelegatingHolder implements CamundaAuthenticationHolder {

  private final List<CamundaAuthenticationHolder> holders;

  public CamundaAuthenticationDelegatingHolder(final List<CamundaAuthenticationHolder> holders) {
    this.holders = holders;
  }

  @Override
  public boolean supports() {
    return true;
  }

  /**
   * Calls the matching {@link CamundaAuthenticationHolder} to set given {@link
   * CamundaAuthentication}, if any found.
   *
   * <p>See {@link CamundaAuthenticationHolder#set(CamundaAuthentication)}
   */
  @Override
  public void set(final CamundaAuthentication authentication) {
    Optional.ofNullable(getMatchingHolder()).ifPresent(c -> c.set(authentication));
  }

  /**
   * Calls the matching {@link CamundaAuthenticationHolder} to obtain a {@link
   * CamundaAuthentication}, if any found.
   *
   * <p>See {@link CamundaAuthenticationHolder#get()}
   */
  @Override
  public CamundaAuthentication get() {
    return Optional.ofNullable(getMatchingHolder())
        .map(CamundaAuthenticationHolder::get)
        .orElse(null);
  }

  protected CamundaAuthenticationHolder getMatchingHolder() {
    return holders.stream().filter(CamundaAuthenticationHolder::supports).findFirst().orElse(null);
  }
}
