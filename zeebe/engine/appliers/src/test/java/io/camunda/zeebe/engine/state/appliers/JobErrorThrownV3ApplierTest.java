/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobErrorThrownV3ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private MutableElementInstanceState elementInstanceState;
  private AppliersTestSetupHelper appliersHelper;

  @BeforeEach
  public void setup() {
    secretReferenceState = processingState.getSecretReferenceState();
    elementInstanceState = processingState.getElementInstanceState();
    appliersHelper = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldRemoveSecretReferencesWhenJobIsDeletedOnCatchEventFound() {
    // given
    final long jobKey = 42L;
    final long elementInstanceKey = 7L;
    final String storeId = "storeA";
    final String secretRef = "secret1";
    secretReferenceState.addPendingSecretReference(storeId, secretRef);
    secretReferenceState.addWaitingJob(storeId, secretRef, jobKey);
    givenElementInstance(elementInstanceKey);

    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setElementInstanceKey(elementInstanceKey)
            .setElementId("task");
    appliersHelper.applyEventToState(jobKey, JobIntent.CREATED, jobRecord);

    // when — a catch event was found, so the job gets deleted from job state
    appliersHelper.applyEventToState(jobKey, JobIntent.ERROR_THROWN, jobRecord);

    // then
    final List<String> visitedRefs = new ArrayList<>();
    secretReferenceState.visitSecretReferencesByJob(
        jobKey,
        (sid, sref) -> {
          visitedRefs.add(sref);
          return true;
        });
    assertThat(visitedRefs).isEmpty();
  }

  @Test
  public void shouldNotRemoveSecretReferencesForOtherJobs() {
    // given
    final long jobKey = 42L;
    final long otherJobKey = 99L;
    final long elementInstanceKey = 7L;
    final String storeId = "storeA";
    final String secretRef = "secret1";
    secretReferenceState.addPendingSecretReference(storeId, secretRef);
    secretReferenceState.addWaitingJob(storeId, secretRef, jobKey);
    secretReferenceState.addWaitingJob(storeId, secretRef, otherJobKey);
    givenElementInstance(elementInstanceKey);

    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setElementInstanceKey(elementInstanceKey)
            .setElementId("task");
    appliersHelper.applyEventToState(jobKey, JobIntent.CREATED, jobRecord);

    // when
    appliersHelper.applyEventToState(jobKey, JobIntent.ERROR_THROWN, jobRecord);

    // then — other job's references are preserved
    final List<Long> visitedJobs = new ArrayList<>();
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretRef,
        key -> {
          visitedJobs.add(key);
          return true;
        });
    assertThat(visitedJobs).containsExactly(otherJobKey);
  }

  private void givenElementInstance(final long elementInstanceKey) {
    final var processInstanceRecord =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setElementId("task")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(3L)
            .setProcessInstanceKey(7L)
            .setVersion(1)
            .setTenantId("<default>");
    elementInstanceState.newInstance(
        elementInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }
}
