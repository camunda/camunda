/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

import java.net.URI;

public record M2MCredentials(URI tokenEndpoint, String clientId, String clientSecret) {
  @Override
  public String toString() {
    return "M2MCredentials[tokenEndpoint="
        + tokenEndpoint
        + ", clientId="
        + clientId
        + ", clientSecret=***]";
  }
}
