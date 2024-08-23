/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.utils;

import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;

public class NoopHTTPCallback implements FutureCallback<HttpResponse> {
  public static final NoopHTTPCallback INSTANCE = new NoopHTTPCallback();

  @Override
  public void completed(final HttpResponse result) {}

  @Override
  public void failed(final Exception ex) {}

  @Override
  public void cancelled() {}
}
