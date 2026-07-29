/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentSecurityPolicyConfigTest {

  @Test
  void saasPolicyImgSrcShouldPermitBlobScheme() {
    // expect:
    assertThat(ContentSecurityPolicyConfig.DEFAULT_SAAS_SECURITY_POLICY)
        .contains("img-src * data: 'self' blob:; ");
  }

  @Test
  void selfManagedPolicyImgSrcShouldPermitBlobScheme() {
    // expect:
    assertThat(ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY)
        .contains("img-src data: 'self' blob:; ");
  }
}
