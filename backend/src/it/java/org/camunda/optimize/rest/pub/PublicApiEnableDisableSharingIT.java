/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicApiEnableDisableSharingIT extends AbstractIT {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void toggleSharingState(boolean enableSharing) {
    // given
    // initialise sharing setting to assert toggled state later
    SettingsResponseDto settings = embeddedOptimizeExtension.getSettingsService().getSettings();
    settings.setSharingEnabled(!enableSharing);
    embeddedOptimizeExtension.getSettingsService().setSettings(settings);

    // then
    // making sure the setting was set correctly
    assertThat(embeddedOptimizeExtension.getSettingsService().getSettings().getSharingEnabled()).contains(!enableSharing);
    // making sure the setting was also propagated to the configuration service
    assertThat(embeddedOptimizeExtension.getConfigurationService().getSharingEnabled()).isEqualTo(!enableSharing);

    // when
    Response response = publicApiClient.toggleSharing(enableSharing, getAccessToken());

    // then
    assertThat(response.getStatus())
      .isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    // Check if the setting was set correctly
    assertThat(embeddedOptimizeExtension.getSettingsService().getSettings().getSharingEnabled()).contains(enableSharing);
    // Check if the setting was also propagated to the configuration service
    assertThat(embeddedOptimizeExtension.getConfigurationService().getSharingEnabled()).isEqualTo(enableSharing);
  }

  private String getAccessToken() {
    return
      Optional.ofNullable(
          embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().getAccessToken())
        .orElseGet(() -> {
          String randomToken = "1_2_Polizei";
          embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(randomToken);
          return randomToken;
        });
  }
}