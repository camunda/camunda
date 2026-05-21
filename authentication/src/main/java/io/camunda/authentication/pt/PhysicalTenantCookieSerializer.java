/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;

@NullMarked
public final class PhysicalTenantCookieSerializer {

  private static final String COOKIE_NAME_PREFIX = "camunda-session-";

  private PhysicalTenantCookieSerializer() {}

  public static DefaultCookieSerializer forPrefixedChain(final String tenantId) {
    final var s = base();
    s.setCookieName(COOKIE_NAME_PREFIX + tenantId);
    s.setCookiePath("/physical-tenant/" + tenantId);
    return s;
  }

  public static DefaultCookieSerializer forUnprefixedDefaultChain() {
    final var s = base();
    s.setCookieName(COOKIE_NAME_PREFIX + "default-root");
    s.setCookiePath("/");
    return s;
  }

  public static CookieHttpSessionIdResolver resolver(final DefaultCookieSerializer serializer) {
    final var resolver = new CookieHttpSessionIdResolver();
    resolver.setCookieSerializer(serializer);
    return resolver;
  }

  private static DefaultCookieSerializer base() {
    final var s = new DefaultCookieSerializer();
    s.setUseSecureCookie(false);
    s.setUseHttpOnlyCookie(true);
    s.setSameSite("Lax");
    return s;
  }
}
