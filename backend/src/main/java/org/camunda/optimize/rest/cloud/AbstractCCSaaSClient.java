/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.camunda.optimize.service.util.configuration.users.CloudUsersConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.CONSOLE;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.MODELER;

@Component
@Conditional(CCSaaSCondition.class)
public abstract class AbstractCCSaaSClient {
  protected static final String GET_ORGS_TEMPLATE = "%s/external/organizations/%s";
  protected static final String GET_CLUSTERS_TEMPLATE = GET_ORGS_TEMPLATE + "/clusters";
  protected static final String GET_USERS_TEMPLATE = GET_ORGS_TEMPLATE + "/members?filter=members";
  protected static final String GET_USER_BY_ID_TEMPLATE = GET_ORGS_TEMPLATE + "/members/%s";
  // E.g.https://modeler.cloud.dev.ultrawombat.com/org/<ORG_ID>
  protected static final String MODELER_URL_TEMPLATE = "https://" + MODELER + ".cloud%s/org/%s";
  // E.g. https://console.cloud.dev.ultrawombat.com
  protected static final String CONSOLE_ROOTURL_TEMPLATE = "https://" + CONSOLE +".cloud%s";
  // E.g. https://console.cloud.dev.ultrawombat.com/org/<ORG_ID>/cluster/<CLUSTER_ID>
  protected static final String CONSOLE_URL_TEMPLATE = CONSOLE_ROOTURL_TEMPLATE + "/org/%s/cluster/%s";
  // Prod domain as fall back
  protected static final String DEFAULT_DOMAIN_WHEN_ERROR_OCCURS = ".camunda.io";


  protected final CloseableHttpClient httpClient;
  protected final ObjectMapper objectMapper;
  protected final ConfigurationService configurationService;

  protected AbstractCCSaaSClient(final ObjectMapper objectMapper, final ConfigurationService configurationService) {
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    HttpClientBuilder builder = HttpClientBuilder.create();

    // Setting a general timeout for external requests to 5000 milliseconds, so that outgoing requests don't block the
    // execution flow from Optimize
    int timeout = 5000;
    builder.setConnectionTimeToLive(timeout, TimeUnit.MILLISECONDS);
    builder.evictIdleConnections(timeout, TimeUnit.MILLISECONDS);
    RequestConfig rc = RequestConfig.custom()
      .setConnectionRequestTimeout(timeout)
      .setConnectTimeout(timeout)
      .setSocketTimeout(timeout)
      .build();
    builder.setDefaultRequestConfig(rc);
    this.httpClient = builder.build();
  }

  // In case the connection times out, the execution will throw a SocketTimeoutException or a
  // ConnectionTimeoutException (depending on the reason), which are both also IOExceptions
  protected CloseableHttpResponse performRequest(final HttpRequestBase request, final String accessToken) throws IOException {
    request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
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
