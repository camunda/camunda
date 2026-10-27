/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.constants;

public final class RestConstants {

  public static final String BACKUP_ENDPOINT = "/backups";

  public static final String X_OPTIMIZE_CLIENT_TIMEZONE = "X-Optimize-Client-Timezone";
  public static final String X_OPTIMIZE_CLIENT_LOCALE = "X-Optimize-Client-Locale";
  public static final String AUTH_COOKIE_TOKEN_VALUE_PREFIX = "Bearer ";
  public static final String OPTIMIZE_AUTHORIZATION_PREFIX = "X-Optimize-Authorization_";
  public static final String OPTIMIZE_REFRESH_TOKEN = "X-Optimize-Refresh-Token";
  public static final String OPTIMIZE_SERVICE_TOKEN = "X-Optimize-Service-Token";

  public static final String CACHE_CONTROL_NO_STORE = "no-store";

  public static final String SAME_SITE_COOKIE_FLAG = "SameSite";
  public static final String SAME_SITE_COOKIE_STRICT_VALUE = "Strict";

  public static final String HTTPS_SCHEME = "https";
  public static final String HTTPS_PREFIX = "https://";
  public static final String HTTP_PREFIX = "http://";

  private RestConstants() {}
}
