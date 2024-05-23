/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;

public interface DataStoreSearchResponse<T> {

  long totalHits();

  String scrollId();

  List<DataStoreSearchHit<T>> hits();

  public interface Builder<T> extends DataStoreObjectBuilder<DataStoreSearchResponse<T>> {

    Builder<T> totalHits(final long totalHits);

    Builder<T> scrollId(final String scrollId);

    Builder<T> hits(final List<DataStoreSearchHit<T>> hits);
  }
}
