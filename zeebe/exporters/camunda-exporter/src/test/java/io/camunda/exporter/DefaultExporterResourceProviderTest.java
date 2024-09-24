/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class DefaultExporterResourceProviderTest {

  @Test
  void shouldReadPolicyListFile() {
    final var policies = new DefaultExporterResourceProvider().getIndexLifeCyclePolicies();

    assertThat(policies.size()).isEqualTo(1);
    assertThat(policies.get("policy_name"))
        .isEqualTo("elasticsearch/policies/policy-template.json");
  }
}
