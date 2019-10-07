/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@Slf4j
public class CompletedProcessInstanceWriter extends AbstractProcessInstanceWriter<ProcessInstanceDto> {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, END_DATE, DURATION, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;

  public CompletedProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                        final ObjectMapper objectMapper,
                                        final DateTimeFormatter dateTimeFormatter) {
    super(objectMapper);
    this.esClient = esClient;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstances) {
    String importItemName = "completed process instances";
    log.debug("Writing [{}] {} to ES", processInstances.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processInstances,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        PRIMITIVE_UPDATABLE_FIELDS,
        objectMapper
      )
    );
  }

  public void deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate) {
    String deletedItemName = "process instances";
    String deletedItemIdentifier = String.format(
      "processDefinitionKey %s endDate past %s",
      processDefinitionKey,
      endDate
    );

    final EsBulkByScrollTaskActionProgressReporter progressReporter = new EsBulkByScrollTaskActionProgressReporter(
      getClass().getName(), esClient, DeleteByQueryAction.NAME
    );
    try {
      progressReporter.start();

      final BoolQueryBuilder filterQuery = boolQuery()
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate)));

      ElasticsearchWriterUtil.doDeleteByQueryRequest(
        esClient,
        filterQuery,
        deletedItemName,
        deletedItemIdentifier,
        PROCESS_INSTANCE_INDEX_NAME
      );
    } finally {
      progressReporter.stop();
    }
  }

  @Override
  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto procInst,
                                                 Set<String> primitiveUpdatableFields,
                                                 ObjectMapper objectMapper) {
    if (procInst.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }

    super.addImportProcessInstanceRequest(bulkRequest, procInst, primitiveUpdatableFields, objectMapper);
  }
}