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
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.camunda.optimize.service.util.configuration.users.CloudAccountsConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSUserClient {

  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;

  public CCSaaSUserClient(final ConfigurationService configurationService,
                          final ObjectMapper objectMapper) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClients.createDefault();
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
  }

  public Optional<CloudUserDto> getCloudUserById(final String userId, final String accessToken) {
    try {
      log.info("Fetching Cloud user by id.");
      final HttpGet request = new HttpGet(String.format(
        "%s/external/organizations/%s/members/%s",
        getCloudUsersConfiguration().getAccountsUrl(),
        getCloudAuthConfiguration().getOrganizationId(),
        URLEncoder.encode(userId, StandardCharsets.UTF_8)
      ));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
          return Optional.empty();
        } else if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(String.format(
            "Unexpected response when fetching cloud user by id: %s", response.getStatusLine().getStatusCode())
          );
        }
        return Optional.ofNullable(objectMapper.readValue(response.getEntity().getContent(), CloudUserDto.class));
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching the cloud user by id.", e);
    }
  }

  public List<CloudUserDto> fetchAllCloudUsers(final String accessToken) {
    try {
      log.info("Fetching Cloud users.");
      final HttpGet request = new HttpGet(String.format(
        "%s/external/organizations/%s/members",
        getCloudUsersConfiguration().getAccountsUrl(),
        getCloudAuthConfiguration().getOrganizationId()
      ));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(String.format(
            "Unexpected response when fetching cloud users: %s", response.getStatusLine().getStatusCode())
          );
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

  private CloseableHttpResponse performRequest(final HttpRequestBase request, final String accessToken) throws IOException {
    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    return httpClient.execute(request);
  }

  private CloudAccountsConfiguration getCloudUsersConfiguration() {
    return configurationService.getUsersConfiguration().getCloud();
  }

  private CloudAuthConfiguration getCloudAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

}
