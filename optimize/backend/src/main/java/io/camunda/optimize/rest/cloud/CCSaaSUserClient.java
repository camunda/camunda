/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSUserClient extends AbstractCCSaaSClient {

  private static final String GET_USER_BY_ID_TEMPLATE = GET_ORGS_TEMPLATE + "/members/%s";
  private static final String GET_USERS_TEMPLATE = GET_ORGS_TEMPLATE + "/members?filter=members";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CCSaaSUserClient.class);

  public CCSaaSUserClient(
      final ConfigurationService configurationService, final ObjectMapper objectMapper) {
    super(objectMapper, configurationService);
  }

  public Optional<CloudUserDto> getCloudUserById(final String userId, final String accessToken) {
    try {
      LOG.info("Fetching Cloud user by id.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_USER_BY_ID_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId(),
                  URLEncoder.encode(userId, StandardCharsets.UTF_8)));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
          return Optional.empty();
        } else if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Unexpected response when fetching cloud user by id: %s",
                  response.getStatusLine().getStatusCode()));
        }
        return Optional.ofNullable(
            objectMapper.readValue(response.getEntity().getContent(), CloudUserDto.class));
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching the cloud user by id.", e);
    }
  }

  public List<CloudUserDto> fetchAllCloudUsers(final String accessToken) {
    try {
      LOG.info("Fetching Cloud users.");
      final HttpGet request =
          new HttpGet(
              String.format(
                  GET_USERS_TEMPLATE,
                  getCloudUsersConfiguration().getAccountsUrl(),
                  getCloudAuthConfiguration().getOrganizationId()));
      try (final CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
          throw new OptimizeRuntimeException(
              String.format(
                  "Unexpected response when fetching cloud users: %s",
                  response.getStatusLine().getStatusCode()));
        }
        return objectMapper.readValue(
            response.getEntity().getContent(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, CloudUserDto.class));
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching Cloud users.", e);
    }
  }
}
