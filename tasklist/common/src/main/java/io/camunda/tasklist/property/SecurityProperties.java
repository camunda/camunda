/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class SecurityProperties {
  public static final String CONTENT_SECURITY_POLICY =
      "default-src 'self';"
          + "connect-src 'self' https: *.mixpanel.com cloudflareinsights.com *.appcues.net wss://api.appcues.net;"
          + "script-src 'self' https: *.chargebee.com *.mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com;"
          + "style-src 'self' https: 'unsafe-inline' *.googleapis.com *.chargebee.com;"
          + "img-src * data: ;"
          + "font-src 'self' data: https://fonts.gstatic.com https://fonts.camunda.io;"
          + "frame-ancestors 'self';"
          + "frame-src 'self' https: *.chargebee.com ;"
          + "child-src;"
          + "worker-src 'self' blob:;"
          + "base-uri 'self';"
          + "form-action 'self';"
          + "object-src 'self';"
          + "script-src-attr 'none';"
          + "sandbox allow-forms allow-scripts allow-same-origin allow-popups";

  private String contentSecurityPolicy = CONTENT_SECURITY_POLICY;

  public String getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  public SecurityProperties setContentSecurityPolicy(String contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
    return this;
  }
}
