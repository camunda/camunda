/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.engine.state.rdbms.OrderedScan.Cursor;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the job table, see {@code db/mybatis/engine-state/JobMapper.xml}. Every
 * statement is scoped to one partition.
 *
 * <p>The page queries of the lookups select whole rows in the order of their index, after a {@link
 * OrderedScan cursor} on {@code (value, job_key)} and up to a limit.
 */
interface JobMapper {

  /** The jobs entered for activation by workers of the type, of any of the tenants. */
  List<JobRow> selectActivatableJobs(
      @Param("partitionId") int partitionId,
      @Param("type") String type,
      @Param("tenantIds") List<String> tenantIds,
      @Param("cursor") @Nullable Cursor cursor,
      @Param("inclusive") boolean inclusive,
      @Param("limit") int limit);

  /** The jobs with a deadline, before the given timestamp if it isn't null. */
  List<JobRow> selectJobsByDeadline(
      @Param("partitionId") int partitionId,
      @Param("before") @Nullable Long before,
      @Param("cursor") @Nullable Cursor cursor,
      @Param("inclusive") boolean inclusive,
      @Param("limit") int limit);

  /** The jobs with a backoff. */
  List<JobRow> selectJobsByBackoff(
      @Param("partitionId") int partitionId,
      @Param("cursor") @Nullable Cursor cursor,
      @Param("inclusive") boolean inclusive,
      @Param("limit") int limit);

  /** The jobs of the process instance; the cursor's value is the process instance key. */
  List<JobRow> selectJobsOfProcessInstance(
      @Param("partitionId") int partitionId,
      @Param("processInstanceKey") long processInstanceKey,
      @Param("cursor") @Nullable Cursor cursor,
      @Param("inclusive") boolean inclusive,
      @Param("limit") int limit);

  /**
   * A job: its state, its lookup entries, and the msgpack {@code JobRecord} without variables. The
   * lookup columns are null while the job has no such entry; a job has at most one entry per
   * lookup. The activation entry is the type and priority, inverted so that ascending order serves
   * the highest priority first, of the job's tenant. The process instance, root process instance
   * and tenant are set on insert and never change; the other columns follow the record.
   */
  record JobRow(
      long key,
      short state,
      String type,
      String tenantId,
      @Nullable String activatableType,
      @Nullable Long invertedPriority,
      @Nullable Long deadline,
      @Nullable Long backoff,
      long processInstanceKey,
      long rootProcessInstanceKey,
      long elementInstanceKey,
      String bpmnProcessId,
      String elementId,
      short jobKind,
      int retries,
      @Nullable String worker,
      byte[] payload) {

    JobRow withState(final short newState) {
      return new JobRow(
          key,
          newState,
          type,
          tenantId,
          activatableType,
          invertedPriority,
          deadline,
          backoff,
          processInstanceKey,
          rootProcessInstanceKey,
          elementInstanceKey,
          bpmnProcessId,
          elementId,
          jobKind,
          retries,
          worker,
          payload);
    }

    JobRow withActivation(
        final @Nullable String newActivatableType, final @Nullable Long newInvertedPriority) {
      return new JobRow(
          key,
          state,
          type,
          tenantId,
          newActivatableType,
          newInvertedPriority,
          deadline,
          backoff,
          processInstanceKey,
          rootProcessInstanceKey,
          elementInstanceKey,
          bpmnProcessId,
          elementId,
          jobKind,
          retries,
          worker,
          payload);
    }

    JobRow withDeadline(final @Nullable Long newDeadline) {
      return new JobRow(
          key,
          state,
          type,
          tenantId,
          activatableType,
          invertedPriority,
          newDeadline,
          backoff,
          processInstanceKey,
          rootProcessInstanceKey,
          elementInstanceKey,
          bpmnProcessId,
          elementId,
          jobKind,
          retries,
          worker,
          payload);
    }

    JobRow withBackoff(final @Nullable Long newBackoff) {
      return new JobRow(
          key,
          state,
          type,
          tenantId,
          activatableType,
          invertedPriority,
          deadline,
          newBackoff,
          processInstanceKey,
          rootProcessInstanceKey,
          elementInstanceKey,
          bpmnProcessId,
          elementId,
          jobKind,
          retries,
          worker,
          payload);
    }

    /** The row with the columns that follow the record taken from the given one. */
    JobRow withRecord(final JobRow record) {
      return new JobRow(
          key,
          state,
          record.type,
          tenantId,
          activatableType,
          invertedPriority,
          deadline,
          backoff,
          processInstanceKey,
          rootProcessInstanceKey,
          record.elementInstanceKey,
          record.bpmnProcessId,
          record.elementId,
          record.jobKind,
          record.retries,
          record.worker,
          record.payload);
    }
  }
}
