/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.dto.optimize.query.ui_configuration.AppName;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.CONSOLE;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.MODELER;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.OPERATE;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.OPTIMIZE;
import static org.camunda.optimize.dto.optimize.query.ui_configuration.AppName.TASKLIST;
import static org.camunda.optimize.rest.constants.RestConstants.HTTPS_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.HTTP_PREFIX;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaasClusterClient extends AbstractCCSaaSClient {
  private Map<AppName, String> webappsLinks;
  private static final Set<AppName> REQUIRED_WEBAPPS_LINKS = Set.of(CONSOLE, OPERATE, OPTIMIZE, MODELER, TASKLIST);

  public CCSaasClusterClient(final ConfigurationService configurationService,
                             final ObjectMapper objectMapper) {
    super(objectMapper, configurationService);
    // To make sure we don't crash when an unknown app is sent, ignore the unknowns
    objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
  }

  public Map<AppName, String> getWebappLinks(final String accessToken) {
    if (MapUtils.isEmpty(webappsLinks)) {
      webappsLinks = retrieveWebappsLinks(accessToken);
    }
    return webappsLinks;
  }

  private Map<AppName, String> retrieveWebappsLinks(String accessToken) {
    try {
      log.info("Fetching cluster metadata.");
      final HttpGet request = new HttpGet(String.format(
        GET_CLUSTERS_TEMPLATE,
        String.format(CONSOLE_ROOTURL_TEMPLATE, retrieveDomainOfRunningInstance()),
        getCloudAuthConfiguration().getOrganizationId()
      ));
      final ClusterMetadata[] metadataForAllClusters;
      try (CloseableHttpResponse response = performRequest(request, accessToken)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(String.format(
            "Unexpected response when fetching cluster metadata: %s", response.getStatusLine().getStatusCode()));
        }
        log.info("Processing response from Cluster metadata");
        metadataForAllClusters = objectMapper.readValue(
          response.getEntity().getContent(),
          ClusterMetadata[].class
        );
      }
      if (metadataForAllClusters != null) {
        String currentClusterId = getCloudAuthConfiguration().getClusterId();
        return Arrays.stream(metadataForAllClusters)
          .filter(cm -> cm.getUuid().equals(currentClusterId))
          .findFirst()
          .map(cluster -> mapToWebappsLinks(cluster.getUrls()))
          // If we can't find cluster metadata for the current cluster, we can't return URLs
          .orElseThrow(() -> new OptimizeRuntimeException(
            "Fetched Cluster metadata successfully, but there was no data for the cluster " + currentClusterId));
      } else {
        throw new OptimizeRuntimeException("Could not fetch Cluster metadata");
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem fetching cluster metadata.", e);
    }
  }

  private Map<AppName, String> mapToWebappsLinks(final Map<AppName, String> urls) {
    // add console and modeler URL if not already present
    final String organizationId = getCloudAuthConfiguration().getOrganizationId();
    final String domain = retrieveDomainOfRunningInstance();
    final String clusterId = getCloudAuthConfiguration().getClusterId();
    urls.computeIfAbsent(MODELER, key -> String.format(MODELER_URL_TEMPLATE, domain, organizationId));
    urls.computeIfAbsent(CONSOLE, key -> String.format(CONSOLE_URL_TEMPLATE, domain, organizationId, clusterId));

    // remove any webapps URL the UI does not require
    return urls.entrySet()
      .stream()
      // Null entries can happen if there is an App that is not present in the AppName Enum
      .filter(entry -> entry.getValue() != null && entry.getKey() != null && REQUIRED_WEBAPPS_LINKS.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String retrieveDomainOfRunningInstance() {
    String rootUrl = configurationService.getContainerAccessUrl()
      .orElseGet(() -> {
        Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
        String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
        Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
        return httpPrefix + configurationService.getContainerHost()
          + ":" + port + configurationService.getContextPath().orElse("");
      });
    // Strip the URL and get only the main part
    // The full URL looks like this, for example: https://bru-2.optimize.dev.ultrawombat.com/ff488019-8082-411e-8abc-46f8597cd7d3/
    Pattern urlPattern = Pattern.compile("^(?:https?://)?(?:[^@/\\n]+@)?(?:www\\.)?([^:/?\\n]+)");
    Matcher matcher = urlPattern.matcher(rootUrl);
    if (matcher.find()) {
      // The pureUrl should look like this, for example: bru-2.optimize.dev.ultrawombat.com
      String pureUrl = matcher.group();
      Pattern domainPattern = Pattern.compile("(?<=" + OPTIMIZE + ").*");
      Matcher domainMatcher = domainPattern.matcher(pureUrl);
      if (domainMatcher.find()) {
        // The domain, if found, should therefore look something like this: .dev.ultrawombat.com
        return domainMatcher.group();
      } else {
        log.warn(
          "The processed URL cannot be parsed: {}. Using the fallback domain {}", pureUrl, DEFAULT_DOMAIN_WHEN_ERROR_OCCURS);
        return DEFAULT_DOMAIN_WHEN_ERROR_OCCURS;
      }
    } else {
      log.warn(
        "The following domain URL cannot be parsed: {}. Using the fallback domain {}", rootUrl, DEFAULT_DOMAIN_WHEN_ERROR_OCCURS);
      return DEFAULT_DOMAIN_WHEN_ERROR_OCCURS;
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ClusterMetadata implements Serializable {
    private String uuid;
    private Map<AppName, String> urls = new EnumMap<>(AppName.class);
  }
}
