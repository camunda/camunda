/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import io.camunda.search.clients.transformers.ServiceTransformer;

public interface FieldSortingTransformer extends ServiceTransformer<String, String> {

  @Override
  String apply(final String domainField);

  default String defaultSortField() {
    return "key";
  }

  static FieldSortingTransformer identity() {
    return t -> t;
  }
}
