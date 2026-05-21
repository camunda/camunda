/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Static-resource configuration for the unified BFF webapp. Maps {@code /webapp/assets/**} to the
 * webjar-packaged FE bundle ({@code classpath:/META-INF/resources/webapp/assets/}) and applies
 * forever-caching headers.
 *
 * <p>Forever-caching is safe because the FE build emits hash-suffixed filenames (cache-busting via
 * filename change). The shell {@code index.html} is intentionally NOT covered here: it is served
 * via {@link io.camunda.webapp.controllers.WebappIndexController} and inherits the no-cache headers
 * applied by the Spring Security filter chain, so a redeploy with a new bundle hash is picked up on
 * the next reload.
 *
 * <p>Active under the {@code tmp-webapp} profile (via {@link
 * io.camunda.webapp.WebappModuleConfiguration}'s component scan) and gated identically to {@link
 * io.camunda.webapp.controllers.WebappIndexController} for symmetry.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebappUiEnabled("tmp-webapp")
public class WebappWebMvcConfig implements WebMvcConfigurer {

  static final String ASSETS_PATH_PATTERN = "/webapp/assets/**";
  static final String ASSETS_CLASSPATH_LOCATION = "classpath:/META-INF/resources/webapp/assets/";
  static final Duration ASSETS_CACHE_MAX_AGE = Duration.ofDays(365);

  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler(ASSETS_PATH_PATTERN)
        .addResourceLocations(ASSETS_CLASSPATH_LOCATION)
        .setCacheControl(CacheControl.maxAge(ASSETS_CACHE_MAX_AGE).cachePublic().immutable());
  }
}
