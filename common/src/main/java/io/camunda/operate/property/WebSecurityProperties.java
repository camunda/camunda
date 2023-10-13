/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

import java.time.Duration;

public class WebSecurityProperties {

  public static final String DEFAULT_SECURITY_POLICY =
             "default-src 'self';"          
           + " base-uri 'self';"
           + " script-src 'self';"
           + " script-src-elem 'self' cdn.jsdelivr.net *.mixpanel.com *.osano.com *.appcues.com;"
           + " connect-src 'self' cdn.jsdelivr.net *.appcues.net wss://api.appcues.net *.osano.com *.mixpanel.com;"
           + " style-src 'self' 'unsafe-inline' cdn.jsdelivr.net *.appcues.com *.osano.com *.mixpanel.com;"
           + " img-src * data:;"
           + " block-all-mixed-content;"
           + " form-action 'self';"
           + " frame-ancestors 'none';"
           + " frame-src 'self' https: *.osano.com *.mixpanel.com;"
           + " object-src 'none';"
           + " font-src 'self' fonts.camunda.io cdn.jsdelivr.net;"
           + " worker-src 'self' *.osano.com *.mixpanel.com blob:;"
           + " sandbox allow-forms allow-scripts allow-same-origin allow-popups";

  // What if a year has 366 days? :)
  // Use recommendation of https://hstspreload.org/
  public static final long DEFAULT_HSTS_MAX_AGE = Duration.ofDays(2 * 365 /* 2 Years */).getSeconds();

  public static final boolean DEFAULT_INCLUDE_SUB_DOMAINS = true;
  private String contentSecurityPolicy = DEFAULT_SECURITY_POLICY;
  private long httpStrictTransportSecurityMaxAgeInSeconds = DEFAULT_HSTS_MAX_AGE;
  private boolean httpStrictTransportSecurityIncludeSubDomains = DEFAULT_INCLUDE_SUB_DOMAINS;

  public String getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  public WebSecurityProperties setContentSecurityPolicy(final String contentSecurityPolicy){
    this.contentSecurityPolicy = contentSecurityPolicy;
    return this;
  }

  public long getHttpStrictTransportSecurityMaxAgeInSeconds() {
    return httpStrictTransportSecurityMaxAgeInSeconds;
  }

  public WebSecurityProperties setHttpStrictTransportSecurityMaxAgeInSeconds(final long httpStrictTransportSecurityMaxAgeInSeconds) {
     this.httpStrictTransportSecurityMaxAgeInSeconds = httpStrictTransportSecurityMaxAgeInSeconds;
     return this;
  }

  public boolean getHttpStrictTransportSecurityIncludeSubDomains() {
    return httpStrictTransportSecurityIncludeSubDomains;
  }

  public WebSecurityProperties setHttpStrictTransPortSecurityIncludeSubDomains(final boolean httpStrictTransPortSecurityIncludeSubDomains) {
    this.httpStrictTransportSecurityIncludeSubDomains = httpStrictTransPortSecurityIncludeSubDomains;
    return this;
  }
}
