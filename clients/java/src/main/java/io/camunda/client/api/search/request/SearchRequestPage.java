/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.api.search.request;

import java.util.List;

public interface SearchRequestPage {

  /** Start the page from. */
  SearchRequestPage from(final Integer value);

  /** Limit the the number of returned entities. */
  SearchRequestPage limit(final Integer value);

  /** Get previous page before the set of values. */
  SearchRequestPage searchBefore(final List<Object> values);

  /** Get next page after the set of values. */
  SearchRequestPage searchAfter(final List<Object> values);
}
