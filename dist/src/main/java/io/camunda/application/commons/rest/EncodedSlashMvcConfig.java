/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

/**
 * Configures the three layers required to handle {@code %2F} (encoded forward slash) in URL path
 * segments — Tomcat connector, Spring Security firewall, and Spring MVC path matching — so that
 * entity IDs containing forward slashes (e.g., OIDC group IDs like {@code /myGroup} from Keycloak)
 * round-trip correctly through the REST layer.
 *
 * <p>This class is unconditional (no {@code @Profile}) so it applies regardless of the active
 * authentication profile. The three cooperating layers are:
 *
 * <ol>
 *   <li>{@link TomcatEncodedSlashConfig}: {@code PASS_THROUGH} — keeps {@code %2F} in the URI so
 *       Tomcat's path normalizer never sees a literal {@code /} from the encoded slash.
 *   <li>{@link #encodedSlashFirewallCustomizer()} below: {@code allowUrlEncodedSlash(true)} —
 *       prevents Spring Security's {@link StrictHttpFirewall} from rejecting {@code %2F} with 400.
 *       This bean is defined here (not in a profile-specific {@code WebSecurityConfig}) so it is
 *       always registered.
 *   <li>{@link #configurePathMatch} below: {@code urlDecode=false} — prevents Spring MVC's {@link
 *       UrlPathHelper} from decoding {@code %2F} to {@code /} before path matching (which would
 *       introduce {@code //} and cause the path sanitizer to collapse it to {@code /}, stripping
 *       the leading slash). After matching, {@code decodePathVariables()} decodes the raw {@code
 *       %2FmyGroup} → {@code /myGroup} for the {@code @PathVariable}.
 * </ol>
 *
 * @see TomcatEncodedSlashConfig
 * @see <a href="https://github.com/camunda/camunda/issues/45215">Issue #45215</a>
 */
@Configuration
public class EncodedSlashMvcConfig implements WebMvcConfigurer {

  /**
   * Allows {@code %2F} through Spring Security's {@link StrictHttpFirewall}. Without this, the
   * firewall rejects any request whose URI contains {@code %2F} with a 400 before it reaches any
   * controller.
   */
  @Bean
  public WebSecurityCustomizer encodedSlashFirewallCustomizer() {
    final var firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    return web -> web.httpFirewall(firewall);
  }

  @Override
  public void configurePathMatch(final PathMatchConfigurer configurer) {
    final UrlPathHelper urlPathHelper = new UrlPathHelper();
    urlPathHelper.setUrlDecode(false);
    configurer.setUrlPathHelper(urlPathHelper);
  }
}
