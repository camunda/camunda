/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Webapps {
  /** Configuration for the Tasklist webapp. */
  @NestedConfigurationProperty private Webapp tasklist = new Webapp();

  /** Configuration for the Operate webapp. */
  @NestedConfigurationProperty private Webapp operate = new Webapp();

  /** Configuration for the Identity webapp. */
  @NestedConfigurationProperty private Webapp identity = new Webapp();

  public Webapp getTasklist() {
    return tasklist;
  }

  public void setTasklist(final Webapp tasklist) {
    this.tasklist = tasklist;
  }

  public Webapp getOperate() {
    return operate;
  }

  public void setOperate(final Webapp operate) {
    this.operate = operate;
  }

  public Webapp getIdentity() {
    return identity;
  }

  public void setIdentity(final Webapp identity) {
    this.identity = identity;
  }
}
