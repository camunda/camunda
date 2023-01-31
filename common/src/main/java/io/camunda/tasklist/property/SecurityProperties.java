/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class SecurityProperties {

  public static final String CONTENT_SECURITY_POLICY =
      "default-src 'self';connect-src 'self' https: *.mixpanel.com cloudflareinsights.com *.appcues.net wss://api.appcues.net;script-src 'self' https: 'unsafe-inline' *.chargebee.com *.mixpanel.com ajax.cloudflare.com static.cloudflareinsights.com;style-src 'self' https: 'unsafe-inline' *.googleapis.com *.chargebee.com;img-src * data: ;font-src 'self' data: https://fonts.gstatic.com https://fonts.camunda.io https://cdn.jsdelivr.net;frame-ancestors;frame-src 'self' https: *.chargebee.com ;child-src;worker-src 'self' blob:;base-uri 'self';form-action 'self';object-src 'none';script-src-attr 'none';upgrade-insecure-requests";
  private String contentSecurityPolicy = CONTENT_SECURITY_POLICY;

  public String getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  public SecurityProperties setContentSecurityPolicy(String contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
    return this;
  }
}
