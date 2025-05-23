/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

public class ContentSecurityPolicyConfig {

  public static final String DEFAULT_SAAS_SECURITY_POLICY =
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

  public static final String DEFAULT_SM_SECURITY_POLICY =
      "default-src 'self';"
          + " base-uri 'self';"
          + " script-src 'self';"
          + " script-src-elem 'self' cdn.jsdelivr.net;"
          + " connect-src 'self' cdn.jsdelivr.net;"
          + " style-src 'self' 'unsafe-inline' cdn.jsdelivr.net;"
          + " img-src * data:;"
          + " block-all-mixed-content;"
          + " form-action 'self';"
          + " frame-ancestors 'none';"
          + " frame-src 'self' https:;"
          + " object-src 'none';"
          + " font-src 'self' fonts.camunda.io cdn.jsdelivr.net;"
          + " worker-src 'self' blob:;"
          + " sandbox allow-forms allow-scripts allow-same-origin allow-popups";

  private boolean enabled = true;
  private String policyDirectives;
  private boolean reportOnly = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }

  public String getPolicyDirectives() {
    return policyDirectives;
  }

  public void setPolicyDirectives(final String policyDirectives) {
    this.policyDirectives = policyDirectives;
  }

  public boolean isReportOnly() {
    return reportOnly;
  }

  public void setReportOnly(final boolean reportOnly) {
    this.reportOnly = reportOnly;
  }
}
