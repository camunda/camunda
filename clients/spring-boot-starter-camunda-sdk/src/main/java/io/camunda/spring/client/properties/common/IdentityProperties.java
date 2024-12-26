/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties.common;

import java.net.URL;

@Deprecated(forRemoval = true, since = "8.7")
public class IdentityProperties extends ApiProperties {
  private URL baseUrl;

  @Override
  public URL getBaseUrl() {
    return baseUrl;
  }

  @Override
  public void setBaseUrl(final URL baseUrl) {
    this.baseUrl = baseUrl;
  }
}
