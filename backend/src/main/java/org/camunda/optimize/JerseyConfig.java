/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.context.annotation.Configuration;

import javax.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("api")
public class JerseyConfig extends ResourceConfig {

  private static final String OPTIMIZE_REST_PACKAGE = "org.camunda.optimize.rest";

  public JerseyConfig() {
    packages(OPTIMIZE_REST_PACKAGE);
    // WADL is not used and having it not explicitly disabled causes a warn log
    property(ServerProperties.WADL_FEATURE_DISABLE, true);
    register(JacksonFeature.class);
  }
}


