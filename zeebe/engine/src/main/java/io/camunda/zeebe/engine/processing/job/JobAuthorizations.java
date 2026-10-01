/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;

/**
 * The authorizations a job worker needs to activate a job and to complete, fail or throw an error
 * for it. A standalone job has no process, so it is authorized by its job type instead of by the
 * process definition it belongs to.
 */
public final class JobAuthorizations {

  private JobAuthorizations() {}

  public static RequiredAuthorization<?> forWorker(final JobRecord job) {
    if (job.isStandalone()) {
      return standaloneJob(PermissionType.UPDATE, job.getType());
    }
    return RequiredAuthorization.of(
        b -> b.processDefinition().updateProcessInstance().resourceId(job.getBpmnProcessId()));
  }

  public static Rejection forbiddenForWorker(final JobRecord job) {
    if (job.isStandalone()) {
      return AuthorizationRejectionMapper.forbidden(
          PermissionType.UPDATE, AuthorizationResourceType.STANDALONE_JOB);
    }
    return AuthorizationRejectionMapper.forbidden(
        PermissionType.UPDATE_PROCESS_INSTANCE, AuthorizationResourceType.PROCESS_DEFINITION);
  }

  public static RequiredAuthorization<?> forCreator(final String jobType) {
    return standaloneJob(PermissionType.CREATE, jobType);
  }

  private static RequiredAuthorization<?> standaloneJob(
      final PermissionType permissionType, final String jobType) {
    return RequiredAuthorization.of(
        b ->
            b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.STANDALONE_JOB))
                .permissionType(AuthzModelMapper.fromProtocol(permissionType))
                .resourceId(jobType));
  }
}
