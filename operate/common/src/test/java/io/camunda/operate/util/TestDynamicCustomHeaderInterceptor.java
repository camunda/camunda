/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.plugin.search.header.CustomHeader;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import java.util.concurrent.atomic.AtomicInteger;

public class TestDynamicCustomHeaderInterceptor implements DatabaseCustomHeaderSupplier {

  public static final String X_CUSTOM_HEADER = "X-Custom-Header";
  public static final String X_CUSTOM_HEADER_VALUE = "MyValue-";
  private static final AtomicInteger COUNTER = new AtomicInteger(0);

  @Override
  public CustomHeader getSearchDatabaseCustomHeader() {
    return new CustomHeader(X_CUSTOM_HEADER, X_CUSTOM_HEADER_VALUE + COUNTER.incrementAndGet());
  }
}
