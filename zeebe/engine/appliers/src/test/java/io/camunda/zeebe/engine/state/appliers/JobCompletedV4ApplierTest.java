/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobCompletedV4ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private AppliersTestSetupHelper appliersHelper;

  @BeforeEach
  public void setup() {
    secretReferenceState = processingState.getSecretReferenceState();
    appliersHelper = new AppliersTestSetupHelper(processingState);
  }

  @Test
  public void shouldRemoveSecretReferencesOnJobCompletion() {
    // given
    final long jobKey = 42L;
    final String storeId = "storeA";
    final String secretRef1 = "secret1";
    final String secretRef2 = "secret2";
    secretReferenceState.addPendingSecretReference(storeId, secretRef1);
    secretReferenceState.addPendingSecretReference(storeId, secretRef2);
    secretReferenceState.addWaitingJob(storeId, secretRef1, jobKey);
    secretReferenceState.addWaitingJob(storeId, secretRef2, jobKey);

    // create the job in state first
    final var jobRecord = new JobRecord().setType("test");
    appliersHelper.applyEventToState(jobKey, JobIntent.CREATED, jobRecord);

    // when
    appliersHelper.applyEventToState(jobKey, JobIntent.COMPLETED, jobRecord);

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
    final String storeId = "storeA";
    final String secretRef = "secret1";
    secretReferenceState.addPendingSecretReference(storeId, secretRef);
    secretReferenceState.addWaitingJob(storeId, secretRef, jobKey);
    secretReferenceState.addWaitingJob(storeId, secretRef, otherJobKey);

    // create the job in state first
    final var jobRecord = new JobRecord().setType("test");
    appliersHelper.applyEventToState(jobKey, JobIntent.CREATED, jobRecord);

    // when
    appliersHelper.applyEventToState(jobKey, JobIntent.COMPLETED, jobRecord);

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
}
