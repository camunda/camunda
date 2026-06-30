/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.search.clients.reader.WaitStateStatisticsReader;
import io.camunda.search.entities.WaitStateStatisticsEntity;
import io.camunda.search.query.WaitStateStatisticsQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.Collections;
import java.util.List;

// STUB: returns an empty result. The data-layer task implements the GROUP BY query
// (SELECT element_id, COUNT(*) ... GROUP BY element_id). See issue #56254 / parent #56239.
public class WaitStateStatisticsDbReader extends AbstractEntityReader<WaitStateStatisticsEntity>
    implements WaitStateStatisticsReader {

  public WaitStateStatisticsDbReader(final RdbmsReaderConfig readerConfig) {
    super(null, readerConfig);
  }

  @Override
  public List<WaitStateStatisticsEntity> aggregate(
      final WaitStateStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return Collections.emptyList();
  }
}
