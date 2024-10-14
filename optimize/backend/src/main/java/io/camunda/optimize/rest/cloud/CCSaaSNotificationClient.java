/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import static io.camunda.optimize.service.util.PanelNotificationConstants.SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import io.camunda.optimize.dto.optimize.cloud.panelnotifications.PanelNotificationRequestDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSNotificationClient extends AbstractCCSaaSClient {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CCSaaSNotificationClient.class);
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
    } catch (final IOException e) {
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
