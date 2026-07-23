/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;

/** Runs the inherited suite against a plain, non-zone-aware coordinator ({@code "0"}). */
final class BareClusterConfigurationManagementApiTest
    extends ClusterConfigurationManagementApiTestBase {

  BareClusterConfigurationManagementApiTest() {
    super(MemberId::from);
  }
}
