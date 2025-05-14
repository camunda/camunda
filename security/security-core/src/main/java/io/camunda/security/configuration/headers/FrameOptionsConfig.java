/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers;

import io.camunda.security.configuration.headers.values.FrameOptionMode;

/**
 * Configures X-Frame-Options header to prevent clickjacking attacks.
 *
 * <p>The X-Frame-Options header indicates whether a browser should be allowed to render a page in a
 * frame, iframe, embed, or object. This prevents clickjacking attacks where malicious sites trick
 * users into clicking on hidden elements.
 *
 * <p>When enabled (default state) with SAMEORIGIN value, pages can only be displayed in frames on
 * the same origin. This allows legitimate same-origin framing while preventing cross-origin
 * clickjacking.
 *
 * <p>Note: Modern applications should also use Content-Security-Policy frame-ancestors directive
 * which provides more granular control and supersedes X-Frame-Options in supporting browsers.
 * However, X-Frame-Options should still be included for compatibility with older browsers.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Frame-Options">MDN:
 *     X-Frame-Options</a>
 * @see <a
 *     href="https://cheatsheetseries.owasp.org/cheatsheets/Clickjacking_Defense_Cheat_Sheet.html">OWASP:
 *     Clickjacking Defense</a>
 */
public class FrameOptionsConfig {
  /**
   * Controls whether the X-Frame-Options header is sent.
   *
   * <p>Default: true (enabled) - Clickjacking protection should remain enabled unless Camunda needs
   * to be embedded in frames. Even then, proper restrictions should be applied rather than
   * disabling entirely.
   */
  private boolean enabled = true;

  /**
   * The framing policy mode.
   *
   * <p>Default: SAMEORIGIN - Allows framing by pages on the same origin while preventing
   * cross-origin framing. This balances security with functionality for applications that use
   * iframes for legitimate same-origin purposes.
   *
   * <p>DENY would be more secure but may break functionality if the application uses any
   * same-origin iframes.
   */
  private FrameOptionMode mode = FrameOptionMode.SAMEORIGIN;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean disabled() {
    return !enabled;
  }

  public FrameOptionMode getMode() {
    return mode;
  }

  public void setMode(final FrameOptionMode mode) {
    this.mode = mode;
  }
}
