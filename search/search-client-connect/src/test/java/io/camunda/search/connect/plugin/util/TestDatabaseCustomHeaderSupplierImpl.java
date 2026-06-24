/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin.util;

import io.camunda.plugin.search.header.CustomHeader;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;

public class TestDatabaseCustomHeaderSupplierImpl implements DatabaseCustomHeaderSupplier {

  public static final String KEY_CUSTOM_HEADER = "foo";
  public static final String VALUE_CUSTOM_HEADER = "bar";

  @Override
  public CustomHeader getSearchDatabaseCustomHeader() {
    return new CustomHeader(KEY_CUSTOM_HEADER, VALUE_CUSTOM_HEADER);
  }
}
