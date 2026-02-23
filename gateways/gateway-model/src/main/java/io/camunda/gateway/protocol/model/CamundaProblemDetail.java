/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * A subclass of {@link ProblemDetail} that restores the default {@code about:blank} value for the
 * {@code type} field.
 *
 * <p>Spring Framework 7 removed the default value for {@code type} (see
 * spring-projects/spring-framework#35294). This class ensures backward compatibility with API
 * consumers expecting the RFC 9457 compliant {@code about:blank} default value.
 */
public class CamundaProblemDetail extends ProblemDetail {

  private static final URI ABOUT_BLANK = URI.create("about:blank");

  public CamundaProblemDetail() {
    super();
    setType(ABOUT_BLANK);
  }

  protected CamundaProblemDetail(final int rawStatusCode) {
    super(rawStatusCode);
    setType(ABOUT_BLANK);
  }

  /**
   * Create a {@code CamundaProblemDetail} instance with the given status code.
   *
   * @param status the status to use
   * @return the created {@code CamundaProblemDetail} instance
   */
  public static CamundaProblemDetail forStatus(final HttpStatusCode status) {
    return forStatus(status.value());
  }

  /**
   * Create a {@code CamundaProblemDetail} instance with the given status value.
   *
   * @param status the status value to use
   * @return the created {@code CamundaProblemDetail} instance
   */
  public static CamundaProblemDetail forStatus(final int status) {
    return new CamundaProblemDetail(status);
  }

  /**
   * Create a {@code CamundaProblemDetail} instance with the given status and detail.
   *
   * @param status the status to use
   * @param detail the detail string
   * @return the created {@code CamundaProblemDetail} instance
   */
  public static CamundaProblemDetail forStatusAndDetail(
      final HttpStatusCode status, final String detail) {
    final CamundaProblemDetail problemDetail = forStatus(status);
    problemDetail.setDetail(detail);
    return problemDetail;
  }

  /**
   * Wrap an existing {@link ProblemDetail} ensuring the {@code type} field is set to {@code
   * about:blank} if not already set.
   *
   * @param source the source ProblemDetail to wrap
   * @return a {@code CamundaProblemDetail} with the same properties as the source, with type
   *     defaulting to {@code about:blank}
   */
  public static CamundaProblemDetail wrap(final ProblemDetail source) {
    if (source == null) {
      return null;
    }
    final CamundaProblemDetail result = forStatus(source.getStatus());
    result.setTitle(source.getTitle());
    result.setDetail(source.getDetail());
    result.setInstance(source.getInstance());
    // Preserve source type if set, otherwise keep about:blank default
    if (source.getType() != null) {
      result.setType(source.getType());
    }
    // Copy any additional properties
    if (source.getProperties() != null) {
      source.getProperties().forEach(result::setProperty);
    }
    return result;
  }
}
