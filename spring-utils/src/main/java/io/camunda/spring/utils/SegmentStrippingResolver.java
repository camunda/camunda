/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Strips N leading path segments before delegating to {@link PathResourceResolver}.
 *
 * <p>Needed because {@code PathPattern.extractPathWithinPattern()} starts collecting at the first
 * {@code *} wildcard rather than the trailing {@code **}, so the resource path handed to the
 * resolver includes the literal and wildcard segments before the {@code **}. This resolver drops
 * the specified number of leading {@code /}-delimited segments so the remaining path can be
 * resolved against the configured classpath location.
 */
public final class SegmentStrippingResolver extends PathResourceResolver {

  private final int segmentsToSkip;

  public SegmentStrippingResolver(final int segmentsToSkip) {
    this.segmentsToSkip = segmentsToSkip;
  }

  @Override
  protected Resource getResource(final String resourcePath, final Resource location)
      throws IOException {
    String path = resourcePath;
    for (int i = 0; i < segmentsToSkip; i++) {
      final int slash = path.indexOf('/');
      if (slash < 0) {
        return null;
      }
      path = path.substring(slash + 1);
    }
    return super.getResource(path, location);
  }
}
