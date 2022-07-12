/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.camunda.optimize.service.util.configuration.users.CloudTokenConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSOrganizationsClient {
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;

  public CCSaaSOrganizationsClient(final ConfigurationService configurationService,
                                   final ObjectMapper objectMapper) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    httpClient = HttpClients.createDefault();
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
  }

  public Optional<String> getSalesPlanType(final String accessToken) {
    try {
      log.info("Fetching cloud organisation.");
      final HttpGet request = new HttpGet(String.format(
        "%s/external/organizations/%s",
        getCloudUsersConfiguration().getUsersUrl(),
        getCloudAuthConfiguration().getOrganizationId()
      ));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(String.format(
            "Unexpected response when fetching cloud organisation: %s", response.getStatusLine().getStatusCode()));
        }
        final AccountsOrganisationResponse responseEntity = objectMapper.readValue(
          response.getEntity().getContent(), AccountsOrganisationResponse.class
        );
        return Optional.ofNullable(responseEntity)
          .flatMap(AccountsOrganisationResponse::getSalesPlan)
          .flatMap(AccountsSalesPlanDto::getType);
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching the cloud organisation.", e);
    }
  }

  private CloseableHttpResponse performRequest(final HttpRequestBase request, final String accessToken) throws IOException {
    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    return httpClient.execute(request);
  }

  private CloudTokenConfiguration getCloudUsersConfiguration() {
    return configurationService.getUsersConfiguration().getCloud();
  }

  private CloudAuthConfiguration getCloudAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class AccountsOrganisationResponse {
    private Optional<AccountsSalesPlanDto> salesPlan;
  }

  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class AccountsSalesPlanDto {
    private Optional<String> type;
  }
}
