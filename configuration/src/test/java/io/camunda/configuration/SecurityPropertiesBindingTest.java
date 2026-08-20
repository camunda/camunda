/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Verifies that CSL security properties bind through {@link Camunda#getSecurity()}. */
@SpringJUnitConfig(UnifiedConfiguration.class)
public class SecurityPropertiesBindingTest {

  @Autowired private Camunda camunda;

  @Test
  void shouldExposeSecurityAsInstanceOfCamundaSecurityLibraryProperties() {
    // given / when
    final Security security = camunda.getSecurity();

    // then
    assertThat(security).isInstanceOf(CamundaSecurityLibraryProperties.class);
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.unprotected-api=true",
        "camunda.security.authorizations.enabled=false",
        "camunda.security.multi-tenancy.checks-enabled=true",
        "camunda.security.transport-layer-security.cluster.enabled=true",
      })
  class WithCslPropertiesSet {

    @Test
    void shouldBindCslPropertiesThroughUnifiedTree(@Autowired final Camunda camunda) {
      // given / when
      final Security security = camunda.getSecurity();

      // then
      assertThat(security.getAuthentication().getMethod()).isEqualTo(AuthenticationMethod.OIDC);
      assertThat(security.getAuthentication().isUnprotectedApi()).isTrue();
      assertThat(security.getAuthorizations().isEnabled()).isFalse();
      assertThat(security.getMultiTenancy().isChecksEnabled()).isTrue();
      assertThat(security.getTransportLayerSecurity().getCluster().isEnabled()).isTrue();
    }
  }
}
