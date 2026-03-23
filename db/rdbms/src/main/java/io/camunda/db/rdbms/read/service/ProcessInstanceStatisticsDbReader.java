/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ProcessInstanceStatisticsDbQuery;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.clients.reader.ProcessInstanceStatisticsReader;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceStatisticsDbReader
    extends AbstractEntityReader<ProcessFlowNodeStatisticsEntity>
    implements ProcessInstanceStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessInstanceStatisticsDbReader.class);

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceStatisticsDbReader(
      final ProcessInstanceMapper processInstanceMapper, final RdbmsReaderConfig readerConfig) {
    super(null, readerConfig);
    this.processInstanceMapper = processInstanceMapper;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessInstanceFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return Collections.emptyList();
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());

    LOG.trace(
        "[RDBMS DB] Query process instance flow node statistics with {}",
        query.filter().processInstanceKey());
    return processInstanceMapper.flowNodeStatistics(
        ProcessInstanceStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())));
  }
}
