/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.automation.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

public class CamundaPasswordEncoder implements PasswordEncoder {

  private final PasswordEncoder delegate;

  public CamundaPasswordEncoder(final PasswordEncoder delegate) {
    this.delegate = delegate;
  }

  @Override
  public String encode(final CharSequence rawPassword) {
    if (StringUtils.hasText(rawPassword) && rawPassword.toString().startsWith("{bcrypt}")) {
      return rawPassword.toString();
    }
    return delegate.encode(rawPassword);
  }

  @Override
  public boolean matches(final CharSequence rawPassword, final String encodedPassword) {
    return delegate.matches(rawPassword, encodedPassword);
  }

  @Override
  public boolean upgradeEncoding(final String encodedPassword) {
    return delegate.upgradeEncoding(encodedPassword);
  }
}
