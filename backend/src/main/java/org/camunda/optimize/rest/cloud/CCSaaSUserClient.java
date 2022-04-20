/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.dto.optimize.cloud.TokenRequestDto;
import org.camunda.optimize.dto.optimize.cloud.TokenResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.camunda.optimize.service.util.configuration.users.CloudTokenConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSUserClient {

  private static final String GRANT_TYPE = "client_credentials";

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;

  private TokenResponseDto accessToken;
  private Instant tokenExpires = Instant.now();

  public CCSaaSUserClient(final ConfigurationService configurationService,
                          final ObjectMapper objectMapper) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    httpClient = HttpClients.createDefault();
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
  }

  public List<CloudUserDto> fetchAllCloudUsers() {
    try {
      log.info("Fetching Cloud users.");
      final HttpGet request = new HttpGet(String.format(
        "%s/external/organizations/%s/members",
        getCloudUsersConfiguration().getUsersUrl(),
        getCloudAuthConfiguration().getOrganizationId()
      ));
      try (final CloseableHttpResponse response = performRequest(request)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(String.format(
            "Unexpected response when fetching cloud users: %s", response.getStatusLine().getStatusCode()));
        }
        return objectMapper.readValue(
          response.getEntity().getContent(),
          objectMapper.getTypeFactory().constructCollectionType(List.class, CloudUserDto.class)
        );
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching Cloud users.", e);
    }
  }

  private CloseableHttpResponse performRequest(HttpRequestBase request) throws IOException {
    refreshAccessTokenIfRequired();
    request.setHeader("Authorization", String.join(" ", accessToken.getTokenType(), accessToken.getAccessToken()));
    return httpClient.execute(request);
  }

  private synchronized void refreshAccessTokenIfRequired() {
    if (Instant.now().plus(15, ChronoUnit.MINUTES).isAfter(tokenExpires)) {
      log.info("Fetching access token for Cloud user retrieval.");
      try {
        final HttpPost request = new HttpPost(getCloudUsersConfiguration().getTokenUrl());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(createTokenRequest()), APPLICATION_JSON));
        try (final CloseableHttpResponse response = httpClient.execute(request)) {
          if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
            throw new OptimizeRuntimeException(
              "Unexpected response while fetching access token: " + response.getStatusLine().getStatusCode());
          }
          final TokenResponseDto tokenResponseDto = objectMapper.readValue(
            response.getEntity().getContent(),
            TokenResponseDto.class
          );
          accessToken = tokenResponseDto;
          tokenExpires = Instant.now().plusSeconds(tokenResponseDto.getExpiresIn());
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("There was a problem fetching access token.", e);
      }
    }
  }

  private TokenRequestDto createTokenRequest() {
    final TokenRequestDto tokenRequestDto = new TokenRequestDto();
    final CloudTokenConfiguration cloudTokenConfig = getCloudUsersConfiguration();
    tokenRequestDto.setClientId(cloudTokenConfig.getClientId());
    tokenRequestDto.setClientSecret(cloudTokenConfig.getClientSecret());
    tokenRequestDto.setAudience(cloudTokenConfig.getAudience());
    tokenRequestDto.setGrantType(GRANT_TYPE);
    return tokenRequestDto;
  }

  private CloudTokenConfiguration getCloudUsersConfiguration() {
    return configurationService.getUsersConfiguration().getCloud();
  }

  private CloudAuthConfiguration getCloudAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

}
