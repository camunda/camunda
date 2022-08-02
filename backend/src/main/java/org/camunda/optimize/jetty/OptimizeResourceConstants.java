/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import com.google.common.collect.ImmutableList;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@NoArgsConstructor
@Configuration
public class OptimizeResourceConstants {

  public static final String REST_API_PATH = "/api";
  public static final String INDEX_PAGE = "/";
  public static final String INDEX_HTML_PAGE = "/index.html";
  public static final String STATIC_RESOURCE_PATH = "/static";

  public static final String STATUS_WEBSOCKET_PATH = "/ws/status";

  public static final ImmutableList<String> NO_CACHE_RESOURCES =
    ImmutableList.<String>builder()
      .add(INDEX_PAGE)
      .add(INDEX_HTML_PAGE)
      .build();
  public static final String ACTUATOR_PORT_PROPERTY_KEY = "management.server.port";
  public static String ACTUATOR_ENDPOINT;
  public static int ACTUATOR_PORT;
  @Value("${management.endpoints.web.base-path:/actuator}")
  public void setActuatorEndpointStatic(String endpoint) {
    OptimizeResourceConstants.ACTUATOR_ENDPOINT = endpoint;
  }
  @Value("${management.server.port:8092}")
  public void setActuatorPortStatic(int port) {
    OptimizeResourceConstants.ACTUATOR_PORT = port;
  }
}
