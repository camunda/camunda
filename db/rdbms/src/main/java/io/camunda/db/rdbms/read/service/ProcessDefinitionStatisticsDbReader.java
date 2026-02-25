/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.search.clients.reader.ProcessDefinitionStatisticsReader;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionStatisticsDbReader
    extends AbstractEntityReader<ProcessFlowNodeStatisticsEntity>
    implements ProcessDefinitionStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionStatisticsDbReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionStatisticsDbReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    super(null, readerConfig);
    this.processDefinitionMapper = processDefinitionMapper;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessDefinitionFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Query process definition flow node statistics with filter {}", query);
    return processDefinitionMapper.flowNodeStatistics(query.filter());
  }
}
