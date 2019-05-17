/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.WebappsEndpointDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Path("/camunda")
@Component
public class CamundaRestService {

  private final ConfigurationService configurationService;

  /**
   * Provides endpoint link to the Camunda Webapplications if enabled.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, WebappsEndpointDto> getCamundaWebappsEndpoint() {
    Map<String, WebappsEndpointDto> engineNameToEndpoints = new HashMap<>();
    for (Map.Entry<String, EngineConfiguration> entry : configurationService.getConfiguredEngines().entrySet()) {
      EngineConfiguration engineConfiguration = entry.getValue();
      WebappsEndpointDto webappsEndpoint = new WebappsEndpointDto();
      String endpointAsString = "";
      if (engineConfiguration.getWebapps().isEnabled()) {
        endpointAsString = engineConfiguration.getWebapps().getEndpoint();
      }
      webappsEndpoint.setEndpoint(endpointAsString);
      webappsEndpoint.setEngineName(engineConfiguration.getName());
      engineNameToEndpoints.put(entry.getKey(), webappsEndpoint);
    }
    return engineNameToEndpoints;
  }
}