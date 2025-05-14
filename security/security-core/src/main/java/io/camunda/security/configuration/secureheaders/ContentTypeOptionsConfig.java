/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Configures X-Content-Type-Options header to prevent MIME type sniffing attacks.
 *
 * <p>The X-Content-Type-Options header prevents browsers from MIME-sniffing a response away from
 * the declared Content-Type. When enabled (default state), it sets the header value to 'nosniff',
 * which instructs browsers to strictly follow the Content-Type header provided by the server.
 *
 * <p>This prevents several security vulnerabilities: - Malicious JavaScript execution when browsers
 * misinterpret file types - XSS attacks through polyglot files (files valid as multiple MIME types)
 * - Drive-by downloads where browsers execute disguised malicious content
 *
 * <p>With 'nosniff' enabled: - Scripts must be served with correct JavaScript MIME types -
 * Stylesheets must be served with text/css - Browsers won't try to "guess" the content type
 *
 * @see <a
 *     href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Content-Type-Options">MDN:
 *     X-Content-Type-Options</a>
 */
public class ContentTypeOptionsConfig {

  /**
   * Controls whether the X-Content-Type-Options: nosniff header is sent.
   *
   * <p>Default: true (enabled) - Highly recommended to keep enabled otherwise application might be
   * exposed to content attacks.
   */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }
}
