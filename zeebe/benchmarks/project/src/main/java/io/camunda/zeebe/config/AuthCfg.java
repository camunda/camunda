/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

public final class AuthCfg {
  private AuthType type = AuthType.NONE;
  private BasicCfg basic = new BasicCfg();

  public AuthType getType() {
    return type;
  }

  public void setType(final AuthType type) {
    this.type = type;
  }

  public BasicCfg getBasic() {
    return basic;
  }

  public void setBasic(final BasicCfg basic) {
    this.basic = basic;
  }

  public enum AuthType {
    NONE,
    BASIC
  }

  public static final class BasicCfg {
    private String username;
    private String password;

    public String getUsername() {
      return username;
    }

    public void setUsername(final String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(final String password) {
      this.password = password;
    }
  }
}
