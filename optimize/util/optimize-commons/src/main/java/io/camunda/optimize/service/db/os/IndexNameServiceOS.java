/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.os.client.OpenSearchOperation;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.util.ObjectBuilderBase;

public class IndexNameServiceOS extends OpenSearchOperation {

  public IndexNameServiceOS(final OptimizeIndexNameService indexNameService) {
    super(indexNameService);
  }

  @Override
  public <T extends ObjectBuilderBase> T applyIndexPrefix(final T request) {
    return super.applyIndexPrefix(request);
  }
}
