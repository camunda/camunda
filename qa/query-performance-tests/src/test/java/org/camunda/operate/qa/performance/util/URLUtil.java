/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class URLUtil {

  @Value("${camunda.operate.qa.queries.operate.host:localhost}")
  private String operateHost;

  @Value("${camunda.operate.qa.queries.operate.port:8080}")
  private Integer operatePort;

  public URI getURL(String urlPart) {
    try {
      return new URL(String.format("http://%s:%s%s", operateHost, operatePort, urlPart)).toURI();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }

  public URI getURL(String urlPart, String urlParams) {
    if (urlParams == null || urlParams.isEmpty()) {
      return getURL(urlPart);
    }
    try {
      return new URL(String.format("%s?%s", getURL(urlPart), urlParams)).toURI();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }
}
