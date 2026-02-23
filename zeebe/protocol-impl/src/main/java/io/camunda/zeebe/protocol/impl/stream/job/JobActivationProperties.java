/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * {@link JobActivationProperties} represents the minimum set of properties required to activate a
 * {@link JobRecordValue} in the engine.
 */
public interface JobActivationProperties extends BufferWriter {

  /**
   * Returns the name of the worker. This is mostly used for debugging purposes.
   *
   * @see JobRecordValue#getWorker()
   */
  DirectBuffer worker();

  /**
   * Returns the variables requested by the worker, or an empty collection if all variables are
   * requested.
   *
   * @see JobRecordValue#getVariables()
   */
  Collection<DirectBuffer> fetchVariables();

  /**
   * Returns the activation timeout of the job, i.e. how long before the job is made activate-able
   * again after activation
   *
   * @see JobRecordValue#getDeadline()
   */
  long timeout();

  /**
   * Returns the identifiers of the tenants that own the jobs requested to be activated by the
   * worker.
   *
   * <p>If {@link TenantFilter#ASSIGNED} then these tenantIds will be ignored and the assigned
   * tenant IDs will be resolved during job activation.
   *
   * @return the identifiers of the tenants for which to activate jobs
   */
  Collection<String> tenantIds();

  /**
   * Returns the type of tenant filter to apply when activating jobs.
   *
   * <ul>
   *   <li>If {@link TenantFilter#ASSIGNED}, then {@link #tenantIds()} will be ignored, and the
   *       accessible tenant IDs will be resolved during job activation.
   *   <li>If {@link TenantFilter#PROVIDED}, then {@link #tenantIds()} will be used.
   * </ul>
   *
   * @see TenantFilter
   */
  TenantFilter tenantFilter();

  /**
   * Returns the claim data of the user that triggered the creation of this stream. The following
   * entries may be available:
   *
   * <ul>
   *   <li>Key: <code>authorized_tenants</code>; Value: a List of Strings defining the user's
   *       authorized tenants.
   *   <li>Key: <code>authorized_username</code>; Value: the Long representation of the
   *       authenticated user's username
   * </ul>
   *
   * @return a Map of authorization data for this record or an empty Map if not set.
   */
  Map<String, Object> claims();
}
