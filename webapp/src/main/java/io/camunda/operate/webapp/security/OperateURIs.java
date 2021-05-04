/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import java.util.Set;

public final class OperateURIs {

  // Used as constants class
   private OperateURIs(){}

   public static final String
      RESPONSE_CHARACTER_ENCODING = "UTF-8",
      ROOT = "/",
      API = "/api/**",

      LDAP_AUTH_PROFILE = "ldap-auth",
      AUTH_PROFILE = "auth",
      LOGIN_RESOURCE = "/api/login",
      LOGOUT_RESOURCE = "/api/logout",
      COOKIE_JSESSIONID = "JSESSIONID",

      SSO_AUTH_PROFILE = "sso-auth",
      CALLBACK_URI = "/sso-callback",
      NO_PERMISSION = "/noPermission",

      X_CSRF_PARAM = "X-CSRF-PARAM",
      X_CSRF_HEADER = "X-CSRF-HEADER",
      X_CSRF_TOKEN = "X-CSRF-TOKEN"
  ;

   public static final String DEFAULT_AUTH = AUTH_PROFILE;
   public static final Set<String> AUTH_PROFILES = Set.of(AUTH_PROFILE,
                                                          LDAP_AUTH_PROFILE,
                                                          SSO_AUTH_PROFILE);

   public static final String[] AUTH_WHITELIST = {
       "/swagger-resources",
       "/swagger-resources/**",
       "/swagger-ui.html",
       "/documentation",
       LOGIN_RESOURCE,
       LOGOUT_RESOURCE
   };

}
