/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

/**
 * Configures Spring MVC's {@link UrlPathHelper} to not pre-decode the request URI before path
 * matching. This is required so that {@code %2F} (an encoded forward slash) in a path segment is
 * treated as an opaque segment token rather than decoded to {@code /} (which would introduce an
 * extra path separator and cause the path normalizer to collapse {@code //myGroup} → {@code
 * myGroup}, silently stripping the leading slash).
 *
 * <p>With {@code urlDecode=false}:
 *
 * <ol>
 *   <li>The path {@code /roles/admin/groups/%2FmyGroup} is matched as-is by {@code AntPathMatcher},
 *       binding {@code {groupId}} to the raw value {@code %2FmyGroup}.
 *   <li>{@link UrlPathHelper#decodePathVariables(jakarta.servlet.http.HttpServletRequest,
 *       java.util.Map)} then decodes each path variable individually, so {@code @PathVariable
 *       String groupId} receives {@code /myGroup} (with the slash preserved).
 * </ol>
 *
 * <p>All other percent-encoded path variable values (e.g., {@code %40} for {@code @}) are decoded
 * by the same {@code decodePathVariables()} call, so the change is transparent to callers.
 *
 * @see TomcatEncodedSlashConfig
 * @see <a href="https://github.com/camunda/camunda/issues/45215">Issue #45215</a>
 */
@Configuration
public class EncodedSlashMvcConfig implements WebMvcConfigurer {

  @Override
  public void configurePathMatch(final PathMatchConfigurer configurer) {
    final UrlPathHelper urlPathHelper = new UrlPathHelper();
    urlPathHelper.setUrlDecode(false);
    configurer.setUrlPathHelper(urlPathHelper);
  }
}
