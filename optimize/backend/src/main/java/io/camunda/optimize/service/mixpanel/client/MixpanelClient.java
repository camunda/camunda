/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class MixpanelClient {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MixpanelClient.class);
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;

  @Autowired
  public MixpanelClient(
      final ConfigurationService configurationService, final ObjectMapper objectMapper) {
    this(configurationService, objectMapper, HttpClients.createDefault());
  }

  public MixpanelClient(
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final CloseableHttpClient httpClient) {
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @PreDestroy
  public void destroy() throws IOException {
    httpClient.close();
  }

  public void importEvent(final MixpanelEvent event) {
    // see https://developer.mixpanel.com/reference/import-events
    final HttpPost importRequest =
        new HttpPost(
            // setting strict=1 to enable request validation by Mixpanel
            getMixpanelConfiguration().getImportUrl()
                + "?strict=1&project_id="
                + getMixpanelConfiguration().getProjectId());
    importRequest.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    importRequest.setHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());

    try {
      importRequest.setEntity(
          new StringEntity(
              objectMapper.writeValueAsString(List.of(event)), ContentType.APPLICATION_JSON));
    } catch (final JsonProcessingException e) {
      throw new OptimizeRuntimeException("Failed to serialize event for Mixpanel.", e);
    }

    try (final CloseableHttpResponse response = httpClient.execute(importRequest)) {
      checkResponse(response);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not import event to Mixpanel.", e);
    }
  }

  private void checkResponse(final CloseableHttpResponse response) {
    try {
      if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
        throw new OptimizeRuntimeException(
            String.format(
                "Unexpected response status on a mixpanel import: %s, response body: %s",
                response.getStatusLine().getStatusCode(),
                EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)));
      }

      final MixpanelImportResponse mixpanelResponse =
          objectMapper.readValue(response.getEntity().getContent(), MixpanelImportResponse.class);
      if (!mixpanelResponse.isSuccessful()) {
        throw new OptimizeRuntimeException(
            String.format(
                "Mixpanel import was not successful, error: %s", mixpanelResponse.getError()));
      }
    } catch (final IOException e) {
      LOG.warn("Could not parse response from Mixpanel.", e);
    }
  }

  private String getAuthHeader() {
    final String auth = getServiceAccount().getUsername() + ":" + getServiceAccount().getSecret();
    return "Basic " + new String(Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8)));
  }

  private MixpanelConfiguration.ServiceAccount getServiceAccount() {
    return getMixpanelConfiguration().getServiceAccount();
  }

  private MixpanelConfiguration getMixpanelConfiguration() {
    return configurationService.getAnalytics().getMixpanel();
  }
}
