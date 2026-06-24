/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.ObjectBuilder;

public final record SearchMatchAllQuery() implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchMatchAllQuery> {

    @Override
    public SearchMatchAllQuery build() {
      return new SearchMatchAllQuery();
    }
  }
}
