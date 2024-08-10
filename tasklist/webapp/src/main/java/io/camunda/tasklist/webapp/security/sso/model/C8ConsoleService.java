/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.sso.model;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
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
  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  private ClusterMetadata clusterMetadata;

  public ClusterMetadata getClusterMetadata() {
    if (clusterMetadata == null) {
      try {
        clusterMetadata = retrieveClusterMetadata();
      } catch (Exception e) {
        LOGGER.error("Couldn't retrieve ClusterMetadata, return null.", e);
        clusterMetadata = null;
      }
    }
    return clusterMetadata;
  }

  private ClusterMetadata retrieveClusterMetadata() {
    final TokenAuthentication authentication =
        (TokenAuthentication) SecurityContextHolder.getContext().getAuthentication();
    if (authentication.isAuthenticated()) {
      final String operationName = "retrieve cluster metadata";
      final RetryPolicy<ClusterMetadata> retryPolicy =
          new RetryPolicy<ClusterMetadata>()
              .handle(IOException.class)
              .withDelay(Duration.ofMillis(500))
              .withMaxAttempts(5)
              .onRetry(e -> LOGGER.debug("Retrying #{} {}", e.getAttemptCount(), operationName))
              .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      LOGGER.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      final ClusterMetadata clusterMetadata =
          Failsafe.with(retryPolicy)
              .get(() -> getClusterMetadataFromConsole(authentication.getAccessToken()));
      if (clusterMetadata != null) {
        return addModelerAndConsoleLinksIfNotExists(clusterMetadata);
      }
    }
    return null;
  }

  private ClusterMetadata addModelerAndConsoleLinksIfNotExists(ClusterMetadata clusterMetadata) {
    final Map<ClusterMetadata.AppName, String> urls = new TreeMap<>(clusterMetadata.getUrls());
    final String organizationId = tasklistProperties.getAuth0().getOrganization();
    final String domain = tasklistProperties.getCloud().getPermissionAudience();
    final String clusterId = tasklistProperties.getCloud().getClusterId();
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
            tasklistProperties.getCloud().getConsoleUrl(),
            tasklistProperties.getAuth0().getOrganization());
    final ResponseEntity<ClusterMetadata[]> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), ClusterMetadata[].class);

    final ClusterMetadata[] clustersMetadata = response.getBody();
    if (clustersMetadata != null) {
      final Optional<ClusterMetadata> clusterMetadataMaybe =
          Arrays.stream(clustersMetadata)
              .filter(cm -> cm.getUuid().equals(tasklistProperties.getCloud().getClusterId()))
              .findFirst();
      return clusterMetadataMaybe.orElse(null);
    } else {
      return null;
    }
  }
}
