/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper.search;

import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.zeebe.gateway.mcp.model.SearchQueryPageRequest;

public class SearchQueryPageMapper {

  public static SearchQueryPage toSearchQueryPage(final SearchQueryPageRequest page) {
    final var builder = SearchQueryPageBuilders.page();
    if (page == null) {
      return builder.build();
    }

    return builder
        .before(page.before())
        .after(page.after())
        .from(page.from())
        .size(page.limit())
        .build();
  }
}
