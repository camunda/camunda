/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class C8ConsoleService {
  private static final Logger LOGGER = LoggerFactory.getLogger(C8ConsoleService.class);

  private static final String CONSOLE_CLUSTER_TEMPLATE = "%s/external/organizations/%s/clusters";
  @Autowired private OperateProperties operateProperties;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  private ClusterMetadata clusterMetadata;

  public ClusterMetadata getClusterMetadata() {
    if (clusterMetadata == null) {
      try {
        final String accessToken =
            ((TokenAuthentication) SecurityContextHolder.getContext().getAuthentication())
                .getAccessToken();
        clusterMetadata = retrieveClusterMetadata(accessToken);
      } catch (Exception e) {
        LOGGER.error("Couldn't retrieve ClusterMetadata, return null.", e);
        clusterMetadata = null;
      }
    }
    return clusterMetadata;
  }

  private ClusterMetadata retrieveClusterMetadata(final String accessToken) throws Exception {
    final ClusterMetadata clusterMetadata =
        RetryOperation.<ClusterMetadata>newBuilder()
            .noOfRetry(5)
            .delayInterval(500, TimeUnit.MILLISECONDS)
            .retryOn(IOException.class)
            .retryConsumer(() -> getClusterMetadataFromConsole(accessToken))
            .message("C8ConsoleService#retrieveClusterMetadata")
            .build()
            .retry();
    return addModelerAndConsoleLinksIfNotExists(clusterMetadata);
  }

  private ClusterMetadata addModelerAndConsoleLinksIfNotExists(ClusterMetadata clusterMetadata) {
    final Map<ClusterMetadata.AppName, String> urls = new TreeMap<>(clusterMetadata.getUrls());
    final String organizationId = operateProperties.getCloud().getOrganizationId();
    final String domain = operateProperties.getCloud().getPermissionAudience();
    final String clusterId = operateProperties.getCloud().getClusterId();
    if (!urls.containsKey(ClusterMetadata.AppName.MODELER)) {
      urls.put(
          ClusterMetadata.AppName.MODELER,
          String.format(
              "https://%s.%s/org/%s", ClusterMetadata.AppName.MODELER, domain, organizationId));
    }
    if (!urls.containsKey(ClusterMetadata.AppName.CONSOLE)) {
      urls.put(
          ClusterMetadata.AppName.CONSOLE,
          String.format(
              "https://%s.%s/org/%s/cluster/%s",
              ClusterMetadata.AppName.CONSOLE, domain, organizationId, clusterId));
    }
    clusterMetadata.setUrls(urls);
    return clusterMetadata;
  }

  private ClusterMetadata getClusterMetadataFromConsole(final String accessToken) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    final String url =
        String.format(
            CONSOLE_CLUSTER_TEMPLATE,
            operateProperties.getCloud().getConsoleUrl(),
            operateProperties.getCloud().getOrganizationId());
    final ResponseEntity<ClusterMetadata[]> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), ClusterMetadata[].class);

    final ClusterMetadata[] clusterMetadatas = response.getBody();
    if (clusterMetadatas != null) {
      final Optional<ClusterMetadata> clusterMetadataMaybe =
          Arrays.stream(clusterMetadatas)
              .filter(cm -> cm.getUuid().equals(operateProperties.getCloud().getClusterId()))
              .findFirst();
      return clusterMetadataMaybe.orElse(null);
    } else {
      return null;
    }
  }
}
