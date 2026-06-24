/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.panelnotification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import io.camunda.optimize.rest.cloud.CCSaaSM2MTokenProvider;
import io.camunda.optimize.rest.cloud.CCSaaSNotificationClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CCSaaSNotificationClientTest {

  @Mock private CCSaaSM2MTokenProvider m2mTokenProvider;
  private CCSaaSNotificationClient underTest;

  @BeforeEach
  public void setup() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getPanelNotificationConfiguration().setM2mTokenAudience("someAudience");
    underTest =
        new CCSaaSNotificationClient(new ObjectMapper(), configurationService, m2mTokenProvider);
  }

  @Test
  public void notificationTokenIsRefreshedWhenExpired() {
    // given
    final TokenResponseDto fastExpiringToken = new TokenResponseDto();
    fastExpiringToken.setAccessToken("expiredAccessToken");
    fastExpiringToken.setExpiresIn(1);
    when(m2mTokenProvider.retrieveM2MToken(any())).thenReturn(fastExpiringToken);

    // when
    underTest.refreshAccessTokenIfRequired();

    // then the first time the token is refreshed because the client was initialised with an already
    // expired timestamp
    verify(m2mTokenProvider, times(1)).retrieveM2MToken(any());

    // when refreshing for the second time after a fast expiring token was returned
    underTest.refreshAccessTokenIfRequired();

    // then the token is refreshed again
    verify(m2mTokenProvider, times(2)).retrieveM2MToken(any());
  }

  @Test
  public void notificationTokenIsNotRefreshedWhenNotExpired() {
    // given
    final TokenResponseDto notExpiredToken = new TokenResponseDto();
    notExpiredToken.setAccessToken("tokenWithLongExpiryTime");
    notExpiredToken.setExpiresIn(999999999);
    when(m2mTokenProvider.retrieveM2MToken(any())).thenReturn(notExpiredToken);

    // when
    underTest.refreshAccessTokenIfRequired();

    // then the first time the token is refreshed because the client was initialised with an already
    // expired timestamp
    verify(m2mTokenProvider, times(1)).retrieveM2MToken(any());

    // when refreshing for the second time after a token was returned that hasn't expired yet
    underTest.refreshAccessTokenIfRequired();

    // then the token was not refreshed a second time (count stays 1)
    verify(m2mTokenProvider, times(1)).retrieveM2MToken(any());
  }
}
