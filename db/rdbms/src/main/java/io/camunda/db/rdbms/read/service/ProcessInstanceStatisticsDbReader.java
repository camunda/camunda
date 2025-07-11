/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessInstanceStatisticsQuery;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceStatisticsDbReader
    extends AbstractEntityReader<ProcessFlowNodeStatisticsEntity>
    implements ProcessInstanceStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessInstanceStatisticsDbReader.class);

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceStatisticsDbReader(final ProcessInstanceMapper processInstanceMapper) {
    super(null);
    this.processInstanceMapper = processInstanceMapper;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessInstanceStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace(
        "[RDBMS DB] Query process instance flow node statistics with {}",
        query.filter().processInstanceKey());
    return processInstanceMapper.flowNodeStatistics(query.filter().processInstanceKey());
  }
}
