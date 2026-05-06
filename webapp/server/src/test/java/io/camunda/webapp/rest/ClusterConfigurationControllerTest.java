/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SaasConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.ServletContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ClusterConfigurationControllerTest {

  @Mock private ActiveComponentsResolver activeComponentsResolver;
  @Mock private SecurityConfiguration securityConfiguration;
  @Mock private MultiTenancyConfiguration multiTenancyConfiguration;
  @Mock private SaasConfiguration saasConfiguration;
  @Mock private ServletContext servletContext;

  private ClusterConfigurationController controller;

  @BeforeEach
  void setUp() {
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);
    when(securityConfiguration.getSaas()).thenReturn(saasConfiguration);
    controller =
        new ClusterConfigurationController(
            activeComponentsResolver,
            securityConfiguration,
            servletContext,
            /* enterprise= */ false,
            /* loginDelegated= */ false,
            DataSize.ofMegabytes(4),
            /* cloudStage= */ null,
            /* cloudMixpanelToken= */ null,
            /* cloudMixpanelApiHost= */ null);
  }

  @Test
  void shouldReturn200WithActiveComponents() {
    // given
    when(activeComponentsResolver.resolve()).thenReturn(List.of("operate", "tasklist"));
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var response = controller.getClusterConfiguration();

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().activeComponents()).containsExactly("operate", "tasklist");
  }

  @Test
  void shouldSetNoCacheHeader() {
    // given
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var response = controller.getClusterConfiguration();

    // then
    assertThat(response.getHeaders().getCacheControl())
        .as("cluster config must never be cached — FE makes auth-flow decisions on this data")
        .isEqualTo(CacheControl.noStore().getHeaderValue());
  }

  @Test
  void shouldPopulateEnterpriseFlag() {
    // given — controller built with enterprise=true
    final var enterpriseController =
        new ClusterConfigurationController(
            activeComponentsResolver,
            securityConfiguration,
            servletContext,
            /* enterprise= */ true,
            false,
            DataSize.ofMegabytes(4),
            null,
            null,
            null);
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = enterpriseController.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.isEnterprise()).isTrue();
  }

  @Test
  void shouldDeriveCanLogoutFromAbsenceOfSaasClusterId() {
    // given — no SaaS cluster → can log out
    when(saasConfiguration.getClusterId()).thenReturn(null);
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.canLogout()).isTrue();
  }

  @Test
  void shouldDeriveCannotLogoutWhenSaasClusterIdIsPresent() {
    // given — SaaS cluster present → logout handled by IdP, not by us
    when(saasConfiguration.getClusterId()).thenReturn("cluster-abc");
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.canLogout()).isFalse();
  }

  @Test
  void shouldExposeMultiTenancyEnabledFlag() {
    // given
    when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.isMultiTenancyEnabled()).isTrue();
  }

  @Test
  void shouldUseServletContextPathAsContextPath() {
    // given
    when(servletContext.getContextPath()).thenReturn("/camunda");
    when(activeComponentsResolver.resolve()).thenReturn(List.of());

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then — the deployment-level servlet context path, never tenant-prefixed
    assertThat(body).isNotNull();
    assertThat(body.contextPath()).isEqualTo("/camunda");
  }

  @Test
  void shouldConvertMaxRequestSizeToBytes() {
    // given — controller built with 4 MB
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.maxRequestSize()).isEqualTo(4 * 1024 * 1024L);
  }

  @Test
  void shouldPopulateCloudFieldsFromSaasConfiguration() {
    // given
    when(saasConfiguration.getOrganizationId()).thenReturn("org-123");
    when(saasConfiguration.getClusterId()).thenReturn("cluster-xyz");
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    final var saasController =
        new ClusterConfigurationController(
            activeComponentsResolver,
            securityConfiguration,
            servletContext,
            false,
            false,
            DataSize.ofMegabytes(4),
            /* cloudStage= */ "prod",
            /* cloudMixpanelToken= */ "token-abc",
            /* cloudMixpanelApiHost= */ "https://api.mixpanel.com");

    // when
    final var body = saasController.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.cloud().organizationId()).isEqualTo("org-123");
    assertThat(body.cloud().clusterId()).isEqualTo("cluster-xyz");
    assertThat(body.cloud().stage()).isEqualTo("prod");
    assertThat(body.cloud().mixpanelToken()).isEqualTo("token-abc");
    assertThat(body.cloud().mixpanelAPIHost()).isEqualTo("https://api.mixpanel.com");
  }

  @Test
  void shouldReturnNullCloudFieldsInSelfManagedDeployment() {
    // given — no SaaS properties set (defaults to null)
    when(saasConfiguration.getOrganizationId()).thenReturn(null);
    when(saasConfiguration.getClusterId()).thenReturn(null);
    when(activeComponentsResolver.resolve()).thenReturn(List.of());
    when(servletContext.getContextPath()).thenReturn("");

    // when
    final var body = controller.getClusterConfiguration().getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.cloud().organizationId()).isNull();
    assertThat(body.cloud().clusterId()).isNull();
    assertThat(body.cloud().stage()).isNull();
    assertThat(body.cloud().mixpanelToken()).isNull();
    assertThat(body.cloud().mixpanelAPIHost()).isNull();
  }
}
