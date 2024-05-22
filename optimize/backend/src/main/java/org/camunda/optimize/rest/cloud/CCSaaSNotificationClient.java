/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import static org.camunda.optimize.service.util.PanelNotificationConstants.SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import org.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationRequestDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSNotificationClient extends AbstractCCSaaSClient {

  private TokenResponseDto accessToken;
  private Instant tokenExpires = Instant.now();
  private final CCSaaSM2MTokenProvider m2mTokenProvider;

  public CCSaaSNotificationClient(
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final CCSaaSM2MTokenProvider m2mTokenProvider) {
    super(objectMapper, configurationService);
    this.m2mTokenProvider = m2mTokenProvider;
  }

  public void sendPanelNotificationToOrg(final PanelNotificationRequestDto notificationRequestDto) {
    log.info(
        "Sending org notification [{}].", notificationRequestDto.getNotification().getUniqueId());
    try {
      final HttpPost request =
          new HttpPost(getSaaSNotificationApiUrl() + SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT);
      final StringEntity notificationRequestBody =
          new StringEntity(
              objectMapper.writeValueAsString(notificationRequestDto),
              ContentType.APPLICATION_JSON);
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      request.setEntity(notificationRequestBody);

      try (final CloseableHttpResponse response = performNotificationRequest(request)) {
        final Response.Status statusCode =
            Response.Status.fromStatusCode(response.getStatusLine().getStatusCode());
        if (!Response.Status.OK.equals(statusCode)) {
          throw new OptimizeRuntimeException(
              "Unexpected response when sending notification: " + statusCode);
        }
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem sending the notification.", e);
    }
  }

  public synchronized void refreshAccessTokenIfRequired() {
    if (Instant.now().plus(15, ChronoUnit.MINUTES).isAfter(tokenExpires)) {
      accessToken =
          m2mTokenProvider.retrieveM2MToken(
              configurationService.getPanelNotificationConfiguration().getM2mTokenAudience());
      tokenExpires = Instant.now().plusSeconds(accessToken.getExpiresIn());
    }
  }

  private CloseableHttpResponse performNotificationRequest(final HttpPost notificationRequest)
      throws IOException {
    refreshAccessTokenIfRequired();
    return performRequest(notificationRequest, accessToken.getAccessToken());
  }

  private String getSaaSNotificationApiUrl() {
    return configurationService.getPanelNotificationConfiguration().getUrl();
  }
}
