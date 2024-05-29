/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.util.StubbedDataStoreClient;
import io.camunda.service.util.StubbedDataStoreClient.RequestStub;
import java.util.List;

public class ProcessInstanceSearchQueryStub implements RequestStub<ProcessInstanceEntity> {

  @Override
  public DataStoreSearchResponse<ProcessInstanceEntity> handle(DataStoreSearchRequest request)
      throws Exception {

    final var processInstance = new ProcessInstanceEntity();
    processInstance.setKey(1234L);
    processInstance.setBpmnProcessId("foo");
    processInstance.setProcessVersion(123);

    final DataStoreSearchHit<ProcessInstanceEntity> hit =
        new DataStoreSearchHit.Builder<ProcessInstanceEntity>()
            .id("1234")
            .source(processInstance)
            .build();

    final DataStoreSearchResponse<ProcessInstanceEntity> response =
        DataStoreSearchResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedDataStoreClient client) {
    client.registerHandler(this);
  }
}
