/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public final class ElasticsearchTestHelper {

  private static final JacksonJsonpMapper MAPPER = new JacksonJsonpMapper();

  // only supports necessary types to unwrap for tests
  public static <T> T unwrapQueryVal(final Query query, final Class<T> clazz) {
    if (query.isTerms()) {
      return query.terms().terms().value().get(0).anyValue().to(clazz, MAPPER);
    }

    if (query.isMatch() && query.match().query().isString()) {
      return (T) query.match().query().stringValue();
    }

    throw new IllegalStateException("not supported field value");
  }
}
