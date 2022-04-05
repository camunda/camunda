/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import org.apache.catalina.Context;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
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
