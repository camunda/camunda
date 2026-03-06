/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_USER_TASK_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex.CANCELED;
import static io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex.IDLE_DURATION_IN_MS;
import static io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex.TOTAL_DURATION_IN_MS;
import static io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex.WORK_DURATION_IN_MS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.FLAT_USER_TASK_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getFlatUserTaskIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.process.FlatUserTaskDto;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.importing.zeebe.cache.OrdinalCache;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class UserTaskWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UserTaskWriter.class);
  private final IndexRepository indexRepository;
  private final OrdinalCache ordinalCache;

  public UserTaskWriter(final IndexRepository indexRepository, final OrdinalCache ordinalCache) {
    this.indexRepository = indexRepository;
    this.ordinalCache = ordinalCache;
  }

  public List<ImportRequestDto> generateFlatUserTaskImports(final List<FlatUserTaskDto> userTasks) {
    final String importItemName = "flat user tasks";
    LOG.debug("Creating imports for [{}].", importItemName);

    final Set<String> combinedKeys =
        userTasks.stream()
            .map(t -> ordinalCache.combinedIndexKey(t.getProcessDefinitionKey(), t.getOrdinal()))
            .collect(Collectors.toSet());
    indexRepository.createMissingIndices(
        FLAT_USER_TASK_INDEX, Set.of(FLAT_USER_TASK_MULTI_ALIAS), combinedKeys);

    return userTasks.stream()
        .map(userTask -> buildImportRequest(userTask, importItemName))
        .toList();
  }

  private ImportRequestDto buildImportRequest(
      final FlatUserTaskDto userTask, final String importItemName) {
    final String indexName =
        getFlatUserTaskIndexAliasName(
            userTask.getProcessDefinitionKey(), ordinalCache.getTickString(userTask.getOrdinal()));
    if (userTask.isNew()) {
      return ImportRequestDto.builder()
          .importName(importItemName)
          .type(RequestType.INDEX)
          .id(userTask.getFlowNodeInstanceId())
          .indexName(indexName)
          .source(userTask)
          .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
          .build();
    } else {
      final Map<String, Object> docs = new HashMap<>();
      if (userTask.getEndDate() != null) {
        docs.put(END_DATE, userTask.getEndDate());
      }
      if (userTask.getTotalDurationInMs() != null) {
        docs.put(TOTAL_DURATION_IN_MS, userTask.getTotalDurationInMs());
      }
      if (userTask.getCanceled() != null) {
        docs.put(CANCELED, userTask.getCanceled());
      }
      if (userTask.getIdleDurationInMs() != null) {
        docs.put(IDLE_DURATION_IN_MS, userTask.getIdleDurationInMs());
      }
      if (userTask.getWorkDurationInMs() != null) {
        docs.put(WORK_DURATION_IN_MS, userTask.getWorkDurationInMs());
      }
      return ImportRequestDto.builder()
          .importName(importItemName)
          .type(RequestType.UPDATE)
          .id(userTask.getFlowNodeInstanceId())
          .indexName(indexName)
          .docs(docs)
          .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
          .build();
    }
  }
}
