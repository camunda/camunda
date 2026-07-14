/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration for the dedicated cluster-admin security chain protecting {@code /cluster/v2/**}.
 */
public class ClusterAdmin {

  /** HTTP Basic authentication for the cluster-admin API. */
  @NestedConfigurationProperty private ClusterAdminBasic basic = new ClusterAdminBasic();

  public ClusterAdminBasic getBasic() {
    return basic;
  }

  public void setBasic(final ClusterAdminBasic basic) {
    this.basic = basic == null ? new ClusterAdminBasic() : basic;
  }
}
