/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.SaasConfiguration;
import org.junit.jupiter.api.Test;

class SaasConfigurationHelperTest {

  @Test
  void shouldReturnNullOrganizationIdWhenConfigIsNull() {
    // given / when / then
    assertThat(SaasConfigurationHelper.organizationId(null)).isNull();
  }

  @Test
  void shouldReturnNullClusterIdWhenConfigIsNull() {
    // given / when / then
    assertThat(SaasConfigurationHelper.clusterId(null)).isNull();
  }

  @Test
  void shouldReturnNotSaasWhenConfigIsNull() {
    // given / when / then
    assertThat(SaasConfigurationHelper.isSaas(null)).isFalse();
  }

  @Test
  void shouldReturnNullOrganizationIdWhenSaasIsNull() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    config.setSaas(null);

    // when / then
    assertThat(SaasConfigurationHelper.organizationId(config)).isNull();
  }

  @Test
  void shouldReturnNullClusterIdWhenSaasIsNull() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    config.setSaas(null);

    // when / then
    assertThat(SaasConfigurationHelper.clusterId(config)).isNull();
  }

  @Test
  void shouldReturnNotSaasWhenSaasIsNull() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    config.setSaas(null);

    // when / then
    assertThat(SaasConfigurationHelper.isSaas(config)).isFalse();
  }

  @Test
  void shouldReturnOrganizationId() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    final SaasConfiguration saas = new SaasConfiguration();
    saas.setOrganizationId("org-123");
    config.setSaas(saas);

    // when / then
    assertThat(SaasConfigurationHelper.organizationId(config)).isEqualTo("org-123");
  }

  @Test
  void shouldReturnClusterId() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    final SaasConfiguration saas = new SaasConfiguration();
    saas.setClusterId("cluster-456");
    config.setSaas(saas);

    // when / then
    assertThat(SaasConfigurationHelper.clusterId(config)).isEqualTo("cluster-456");
  }

  @Test
  void shouldReturnIsSaasWhenClusterIdPresent() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    final SaasConfiguration saas = new SaasConfiguration();
    saas.setClusterId("cluster-456");
    config.setSaas(saas);

    // when / then
    assertThat(SaasConfigurationHelper.isSaas(config)).isTrue();
  }

  @Test
  void shouldReturnNotSaasWhenClusterIdAbsent() {
    // given
    final SecurityConfiguration config = new SecurityConfiguration();
    final SaasConfiguration saas = new SaasConfiguration();
    config.setSaas(saas);

    // when / then
    assertThat(SaasConfigurationHelper.isSaas(config)).isFalse();
  }
}
