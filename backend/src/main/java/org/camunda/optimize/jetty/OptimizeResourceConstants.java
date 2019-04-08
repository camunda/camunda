/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import com.google.common.collect.ImmutableList;

public class OptimizeResourceConstants {

  public static final String REST_API_PATH = "/api";

  public static final String INDEX_PAGE = "/";
  public static final String INDEX_HTML_PAGE = "/index.html";
  public static final String LOGIN_PAGE = "/login";
  public static final String LICENSE_PAGE = "/license.html";
  public static final String LICENSE_CSS = "/licenseStyles.css";
  public static final String LICENSE_JS = "/license.js";
  public static final String ERROR_PAGE = "/error.html";

  public static final ImmutableList<String> NO_CACHE_RESOURCES =
    ImmutableList.<String>builder()
      .add(INDEX_PAGE)
      .add(INDEX_HTML_PAGE)
      .add(LOGIN_PAGE)
      .add(ERROR_PAGE)
      .add(LICENSE_PAGE)
      .add(LICENSE_CSS)
      .add(LICENSE_JS)
      .build();
}
