/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.cloud.TokenRequestDto;
import io.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSM2MTokenProvider extends AbstractCCSaaSClient {

  private static final String TOKEN_REQUEST_GRANT_TYPE = "client_credentials";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CCSaaSM2MTokenProvider.class);

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
    LOG.info("Requesting M2M token");
    try {
      final HttpPost request = new HttpPost(getTokenProviderUrl());
      final StringEntity notificationRequestBody =
          new StringEntity(
              objectMapper.writeValueAsString(tokenRequestDto), ContentType.APPLICATION_JSON);
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      request.setEntity(notificationRequestBody);

      try (final CloseableHttpResponse response = performRequest(request)) {
        final HttpStatus status = HttpStatus.resolve(response.getStatusLine().getStatusCode());
        if (!HttpStatus.OK.equals(status)) {
          throw new OptimizeRuntimeException(
              "Unexpected response when retrieving M2M token: " + status);
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
