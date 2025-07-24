/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

public class OidcProperties {

  private Audiences audience = new Audiences();

  public Audiences getAudience() {
    return audience;
  }

  public void setAudience(final Audiences audience) {
    this.audience = audience;
  }

  public static class Audiences {

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
