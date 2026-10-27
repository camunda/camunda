/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import org.apache.catalina.Context;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.stereotype.Component;

@Component
public class SameSiteCookieTomcatContextCustomizer implements TomcatContextCustomizer {

  @Override
  public void customize(final Context context) {
    final Rfc6265CookieProcessor cookieProcessor = new Rfc6265CookieProcessor();
    cookieProcessor.setSameSiteCookies("Lax");
    context.setCookieProcessor(cookieProcessor);
  }
}
