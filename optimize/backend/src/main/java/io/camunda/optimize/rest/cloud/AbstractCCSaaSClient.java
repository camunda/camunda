/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.optimize.service.util.configuration.users.CloudUsersConfiguration;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public abstract class AbstractCCSaaSClient {
  protected static final String GET_ORGS_TEMPLATE = "%s/external/organizations/%s";

  protected final CloseableHttpClient httpClient;
  protected final ObjectMapper objectMapper;
  protected final ConfigurationService configurationService;

  protected AbstractCCSaaSClient(
      final ObjectMapper objectMapper, final ConfigurationService configurationService) {
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    final HttpClientBuilder builder = HttpClientBuilder.create();

    // Setting a general timeout for external requests to 5000 milliseconds, so that outgoing
    // requests don't block the
    // execution flow from Optimize
    final int timeout = 5000;
    builder.setConnectionTimeToLive(timeout, TimeUnit.MILLISECONDS);
    builder.evictIdleConnections(timeout, TimeUnit.MILLISECONDS);
    final RequestConfig rc =
        RequestConfig.custom()
            .setConnectionRequestTimeout(timeout)
            .setConnectTimeout(timeout)
            .setSocketTimeout(timeout)
            .build();
    builder.setDefaultRequestConfig(rc);
    httpClient = builder.build();
  }

  // In case the connection times out, the execution will throw a SocketTimeoutException or a
  // ConnectionTimeoutException (depending on the reason), which are both also IOExceptions
  public CloseableHttpResponse performRequest(
      final HttpRequestBase request, final String accessToken) throws IOException {
    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    return httpClient.execute(request);
  }

  protected CloseableHttpResponse performRequest(final HttpRequestBase request) throws IOException {
    return httpClient.execute(request);
  }

  protected CloudUsersConfiguration getCloudUsersConfiguration() {
    return configurationService.getUsersConfiguration().getCloud();
  }

  protected CloudAuthConfiguration getCloudAuthConfiguration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
  }
}
