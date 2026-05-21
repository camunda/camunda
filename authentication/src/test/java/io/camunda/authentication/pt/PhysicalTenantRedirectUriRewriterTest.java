/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhysicalTenantRedirectUriRewriterTest {

  @Test
  void shouldInsertTenantSegmentBetweenBaseUrlAndLoginCallback() {
    assertThat(
            PhysicalTenantRedirectUriRewriter.rewrite(
                "{baseUrl}/login/oauth2/code/{registrationId}", "tenanta"))
        .isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldNotRewriteWhenAlreadyPrefixed() {
    final var input = "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}";
    assertThat(PhysicalTenantRedirectUriRewriter.rewrite(input, "tenanta")).isEqualTo(input);
  }

  @Test
  void shouldRewriteAbsoluteUri() {
    assertThat(
            PhysicalTenantRedirectUriRewriter.rewrite(
                "https://oc.example.com/login/oauth2/code/idpOne", "default"))
        .isEqualTo("https://oc.example.com/physical-tenant/default/login/oauth2/code/idpOne");
  }

  @Test
  void shouldRejectBlankTenantId() {
    assertThatThrownBy(
            () -> PhysicalTenantRedirectUriRewriter.rewrite("{baseUrl}/login/oauth2/code/x", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
