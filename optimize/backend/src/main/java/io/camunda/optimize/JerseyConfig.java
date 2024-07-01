/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ApplicationPath("api")
public class JerseyConfig extends ResourceConfig {

  private static final String OPTIMIZE_REST_PACKAGE = "io.camunda.optimize.rest";

  public JerseyConfig() {
    packages(OPTIMIZE_REST_PACKAGE);
    // WADL is not used and having it not explicitly disabled causes a warn log
    property(ServerProperties.WADL_FEATURE_DISABLE, true);
    register(JacksonFeature.class);
  }
}
