/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.oidc;

public class OidcProperties {

  private Audience audience = new Audience();

  public Audience getAudience() {
    return audience;
  }

  public void setAudience(final Audience audience) {
    this.audience = audience;
  }

  public static class Audience {

    private String identity = "camunda-identity-resource-server";
    private String operate = "operate-api";
    private String tasklist = "tasklist-api";
    private String zeebe = "zeebe-api";

    public String getIdentity() {
      return identity;
    }

    public void setIdentity(final String identity) {
      this.identity = identity;
    }

    public String getOperate() {
      return operate;
    }

    public void setOperate(final String operate) {
      this.operate = operate;
    }

    public String getTasklist() {
      return tasklist;
    }

    public void setTasklist(final String tasklist) {
      this.tasklist = tasklist;
    }

    public String getZeebe() {
      return zeebe;
    }

    public void setZeebe(final String zeebe) {
      this.zeebe = zeebe;
    }
  }
}
