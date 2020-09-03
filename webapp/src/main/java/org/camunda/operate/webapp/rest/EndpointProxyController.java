/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = OperateProperties.PREFIX,name = "management.compatibility.enabled", havingValue = "true", matchIfMissing = true)
public class EndpointProxyController {

  private static final Logger logger = LoggerFactory.getLogger(EndpointProxyController.class);

  public static final String
      OLD_READINESS = "/api/check",
      NEW_READINESS = "health/readiness",
      OLD_LIVENESS = "/actuator/health",
      NEW_LIVENESS = "health/liveness",
      OLD_METRICS = "/actuator/prometheus",
      NEW_METRICS = "metrics"
  ;

  @Autowired
  Environment environment;

  private int managementPort;
  private String managementBasePath;
  private Map<String, Boolean> deprecatedMessagesWritten = new HashMap<>();

  @PostConstruct
  private void init() {
    managementPort = getManagementPort();
    managementBasePath = getManagementBasePath();
    logger.warn("Management server compatibility mode enabled. Disable by setting 'camunda.operate.management.compatibility.enabled' to 'false'");
  }

  @GetMapping(value = OLD_READINESS)
  public ResponseEntity<String> readiness() {
    return proxyGetRequest(OLD_READINESS,NEW_READINESS);
  }

  @GetMapping(value = OLD_LIVENESS)
  public ResponseEntity<String> liveness(){
    return proxyGetRequest(OLD_LIVENESS, NEW_LIVENESS);
  }

  @GetMapping(value = OLD_METRICS)
  public ResponseEntity<String> metrics(){
    return proxyGetRequest(OLD_METRICS, NEW_METRICS);
  }

  protected void writeDeprecateMessageIfNeeded(String deprecatedPath, String newPath) {
    if (!deprecatedMessagesWritten.containsKey(deprecatedPath)) {
      logger.warn(
          String.format("This endpoint %s is DEPRECATED and will be removed in future versions. Use %d:%s instead.", deprecatedPath, managementPort, newPath));
      deprecatedMessagesWritten.put(deprecatedPath, true);
    }
  }

  protected ResponseEntity<String> proxyGetRequest(String oldPath, String newPath){
    String currentPath = managementBasePath + newPath;
    try {
      URI uri = new URI("http", null, "localhost", managementPort, null, null, null);
      uri = UriComponentsBuilder.fromUri(uri).path(currentPath).build(true).toUri();
      writeDeprecateMessageIfNeeded(oldPath, currentPath);
      return executeRequest(uri);
    } catch (URISyntaxException e) {
      throw new OperateRuntimeException(String.format("Error in building proxy uri for %d:%s", managementPort, currentPath), e);
    }
  }

  protected ResponseEntity<String> executeRequest(URI uri) {
    RestTemplate restTemplate = new RestTemplate();
    try {
      return restTemplate.getForEntity(uri, String.class);
    } catch (HttpStatusCodeException e) {
      return ResponseEntity.status(e.getRawStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
    }
  }

  protected int getManagementPort(){
    try {
      return Integer.parseInt(environment.getProperty("management.server.port", "8081"));
    } catch (NumberFormatException nfe) {
      throw new OperateRuntimeException("Error in retrieving management server port", nfe);
    }
  }

  protected String getManagementBasePath(){
    return environment.getProperty("management.endpoints.web.base-path","/");
  }
}
