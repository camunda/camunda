/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import java.util.List;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Makes the Elasticsearch 9 client talk to Elasticsearch 8 servers.
 *
 * <p>The client stamps every request with {@code compatible-with=9} on {@code Accept} and {@code
 * Content-Type} and overwrites any default header we configure, so the only place left to change it
 * is the HTTP layer.
 *
 * <p>Remove this interceptor once all clusters run Elasticsearch 9.
 */
public final class ElasticsearchCompatibilityInterceptor implements HttpRequestInterceptor {

  public static final ElasticsearchCompatibilityInterceptor INSTANCE =
      new ElasticsearchCompatibilityInterceptor();

  private static final String CLIENT_VERSION = "compatible-with=9";
  private static final String COMPATIBLE_MEDIA_TYPE =
      "application/vnd.elasticsearch+json; compatible-with=8";
  private static final List<String> VERSIONED_HEADERS =
      List.of(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE);

  private ElasticsearchCompatibilityInterceptor() {}

  @Override
  public void process(
      final HttpRequest request, final EntityDetails entity, final HttpContext context) {
    // the client only ever sets this media type, so replacing the whole value is safe; requests
    // that carry another media type (raw calls) are left alone
    for (final var name : VERSIONED_HEADERS) {
      final var header = request.getFirstHeader(name);
      if (header != null && header.getValue().contains(CLIENT_VERSION)) {
        request.setHeader(name, COMPATIBLE_MEDIA_TYPE);
      }
    }
  }
}
