/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.optimize.cloud.TokenRequestDto;
import org.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSM2MTokenProvider extends AbstractCCSaaSClient {
  private static final String TOKEN_REQUEST_GRANT_TYPE = "client_credentials";

  protected CCSaaSM2MTokenProvider(
      final ObjectMapper objectMapper, final ConfigurationService configurationService) {
    super(objectMapper, configurationService);
  }

  public TokenResponseDto retrieveM2MToken(final String audience) {
    final TokenRequestDto tokenRequestDto =
        TokenRequestDto.builder()
            .grantType(TOKEN_REQUEST_GRANT_TYPE)
            .audience(audience)
            .clientId(getM2MClientId())
            .clientSecret(getM2MClientSecret())
            .build();
    log.info("Requesting M2M token");
    try {
      final HttpPost request = new HttpPost(getTokenProviderUrl());
      final StringEntity notificationRequestBody =
          new StringEntity(
              objectMapper.writeValueAsString(tokenRequestDto), ContentType.APPLICATION_JSON);
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      request.setEntity(notificationRequestBody);

      try (final CloseableHttpResponse response = performRequest(request)) {
        final Response.Status statusCode =
            Response.Status.fromStatusCode(response.getStatusLine().getStatusCode());
        if (!Response.Status.OK.equals(statusCode)) {
          throw new OptimizeRuntimeException(
              "Unexpected response when retrieving M2M token: " + statusCode);
        }
        return objectMapper.readValue(
            EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8),
            TokenResponseDto.class);
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("There was a problem retrieving the M2M token.", e);
    }
  }

  private String getTokenProviderUrl() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration().getTokenUrl();
  }

  private String getM2MClientId() {
    return configurationService.getM2mAuth0ClientConfiguration().getM2mClientId();
  }

  private String getM2MClientSecret() {
    return configurationService.getM2mAuth0ClientConfiguration().getM2mClientSecret();
  }
}
