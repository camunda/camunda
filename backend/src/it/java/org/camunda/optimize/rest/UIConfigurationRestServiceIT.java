/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Sets;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.camunda.optimize.service.util.configuration.ui.TextColorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class UIConfigurationRestServiceIT extends AbstractIT {
  private final String WEBHOOK_1_NAME = "webhook1";
  private final String WEBHOOK_2_NAME = "webhook2";

  @Test
  public void getHeaderCustomization() {
    // when
    final UIConfigurationResponseDto actualConfiguration = uiConfigurationClient.getUIConfiguration();

    // then
    HeaderCustomizationDto configurationHeader = actualConfiguration.getHeader();
    assertThat(configurationHeader).isNotNull();
    assertThat(configurationHeader.getTextColor()).isEqualTo(TextColorType.DARK);
    assertThat(configurationHeader.getBackgroundColor()).isEqualTo("#FFFFFF");
    assertThat(configurationHeader.getLogo()).startsWith("data:");
  }

  @Test
  public void logoutHidden() {
    //given
    embeddedOptimizeExtension.getConfigurationService().getUiConfiguration().setLogoutHidden(true);

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isLogoutHidden()).isTrue();
  }

  @Test
  public void logoutVisible() {
    //given
    embeddedOptimizeExtension.getConfigurationService().getUiConfiguration().setLogoutHidden(false);

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isLogoutHidden()).isFalse();
  }

  @Test
  public void sharingEnabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(true);

    // when
    final UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isSharingEnabled()).isTrue();
  }

  @Test
  public void sharingDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);

    // when
    final UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isSharingEnabled()).isFalse();
  }

  @Test
  public void getDefaultCamundaWebappsEndpoint() {
    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints).isNotEmpty();
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint).isNotNull();
    assertThat(defaultEndpoint.getEndpoint()).isEqualTo("http://localhost:8080/camunda");
    assertThat(defaultEndpoint.getEngineName()).isEqualTo(engineIntegrationExtension.getEngineName());
  }

  @Test
  public void getCustomCamundaWebappsEndpoint() {
    // given
    setWebappsEndpoint("foo");

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints).isNotEmpty();
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint).isNotNull();
    assertThat(defaultEndpoint.getEndpoint()).isEqualTo("foo");
    assertThat(defaultEndpoint.getEngineName()).isEqualTo(engineIntegrationExtension.getEngineName());
  }

  @Test
  public void disableWebappsEndpointReturnsEmptyEndpoint() {
    // given
    setWebappsEnabled(false);

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();


    // then
    Map<String, WebappsEndpointDto> webappsEndpoints = response.getWebappsEndpoints();
    assertThat(webappsEndpoints).isNotEmpty();
    WebappsEndpointDto defaultEndpoint = webappsEndpoints.get(DEFAULT_ENGINE_ALIAS);
    assertThat(defaultEndpoint).isNotNull();
    assertThat(defaultEndpoint.getEndpoint()).isEmpty();
  }


  @Test
  public void emailNotificationIsEnabled() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEmailEnabled(true);

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isEmailEnabled()).isTrue();
  }

  @Test
  public void emailNotificationIsDisabled() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEmailEnabled(false);

    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isEmailEnabled()).isFalse();
  }

  @Test
  public void getOptimizeVersion() {
    // when
    UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.getOptimizeVersion()).isEqualTo(Version.RAW_VERSION);
  }

  @Test
  public void getWebhooks() {
    // given
    Map<String, WebhookConfiguration> webhookMap = uiConfigurationClient.createSimpleWebhookConfigurationMap(
      Sets.newHashSet(
        WEBHOOK_2_NAME,
        WEBHOOK_1_NAME
      ));
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookMap);

    // when
    List<String> allWebhooks = uiConfigurationClient.getUIConfiguration().getWebhooks();

    // then
    assertThat(allWebhooks).containsExactly(WEBHOOK_1_NAME, WEBHOOK_2_NAME);
  }

  @Test
  public void tenantsAvailable_oneTenant() {
    // given
    createTenant("tenant1");

    // when
    final UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isTenantsAvailable()).isTrue();
  }

  @Test
  public void tenantsAvailable_noTenants() {
    // given

    // when
    final UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isTenantsAvailable()).isFalse();
  }

  @Test
  public void getTelemetryFlags() {
    // when
    final UIConfigurationResponseDto response = uiConfigurationClient.getUIConfiguration();

    // then
    assertThat(response.isMetadataTelemetryEnabled())
      .isEqualTo(embeddedOptimizeExtension.getSettingsService().getSettings().isMetadataTelemetryEnabled());
    assertThat(response.isSettingsManuallyConfirmed())
      .isEqualTo(embeddedOptimizeExtension.getSettingsService().getSettings().isManuallyConfirmed());
  }

  private void setWebappsEndpoint(String webappsEndpoint) {
    embeddedOptimizeExtension
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .getWebapps()
      .setEndpoint(webappsEndpoint);
  }

  private void setWebappsEnabled(boolean enabled) {
    embeddedOptimizeExtension
      .getConfigurationService()
      .getConfiguredEngines()
      .get(DEFAULT_ENGINE_ALIAS)
      .getWebapps()
      .setEnabled(enabled);
  }

  protected void createTenant(final String tenantId) {
    final TenantDto tenantDto = new TenantDto(tenantId, tenantId, DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
  }
}
