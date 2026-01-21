/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.DbQueryPage;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.Copyable;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface ProcessInstanceMapper {

  void insert(ProcessInstanceDbModel processInstance);

  void updateStateAndEndDate(EndProcessInstanceDto dto);

  void incrementIncidentCount(Long processInstanceKey);

  void decrementIncidentCount(Long processInstanceKey);

  void insertTags(ProcessInstanceDbModel processInstance);

  ProcessInstanceEntity findOne(Long processInstanceKey);

  Long count(ProcessInstanceDbQuery filter);

  List<ProcessInstanceEntity> search(ProcessInstanceDbQuery filter);

  List<ProcessFlowNodeStatisticsEntity> flowNodeStatistics(long processInstanceKey);

  int deleteByKeys(List<Long> processInstanceKeys);

  List<Long> selectExpiredProcessInstances(SelectExpiredProcessInstancesDto dto);

  int updateHistoryCleanupDate(UpdateHistoryCleanupDateDto dto);

  record UpdateHistoryCleanupDateDto(
      List<Long> processInstanceKeys, OffsetDateTime historyCleanupDate)
      implements Copyable<UpdateHistoryCleanupDateDto> {

    @Override
    public UpdateHistoryCleanupDateDto copy(
        final Function<
                ObjectBuilder<UpdateHistoryCleanupDateDto>,
                ObjectBuilder<UpdateHistoryCleanupDateDto>>
            copyFunction) {
      return copyFunction
          .apply(
              new UpdateHistoryCleanupDateDto.Builder()
                  .processInstanceKeys(new ArrayList<>(processInstanceKeys))
                  .historyCleanupDate(historyCleanupDate))
          .build();
    }

    public static class Builder implements ObjectBuilder<UpdateHistoryCleanupDateDto> {

      private List<Long> processInstanceKeys = new ArrayList<>();
      private OffsetDateTime historyCleanupDate;

      public UpdateHistoryCleanupDateDto.Builder processInstanceKey(final long processInstanceKey) {
        processInstanceKeys.add(processInstanceKey);
        return this;
      }

      public UpdateHistoryCleanupDateDto.Builder processInstanceKeys(
          final List<Long> processInstanceKeys) {
        this.processInstanceKeys = processInstanceKeys;
        return this;
      }

      public UpdateHistoryCleanupDateDto.Builder historyCleanupDate(
          final OffsetDateTime historyCleanupDate) {
        this.historyCleanupDate = historyCleanupDate;
        return this;
      }

      @Override
      public UpdateHistoryCleanupDateDto build() {
        return new UpdateHistoryCleanupDateDto(processInstanceKeys, historyCleanupDate);
      }
    }
  }

  record EndProcessInstanceDto(
      long processInstanceKey,
      ProcessInstanceEntity.ProcessInstanceState state,
      OffsetDateTime endDate) {}

  record SelectExpiredProcessInstancesDto(
      int partitionId, OffsetDateTime cleanupDate, DbQueryPage page) {}
}
