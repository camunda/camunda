/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ZeebeStateExtension.class)
final class JobBatchCollectorTest {
  private final RecordLengthEvaluator lengthEvaluator = new RecordLengthEvaluator();

  private ZeebeDb db;
  private TransactionContext txContext;
  private MutableZeebeState state;

  private JobBatchCollector collector;

  @BeforeEach
  void beforeEach() {
    collector =
        new JobBatchCollector(state.getJobState(), state.getVariableState(), lengthEvaluator);
  }

  @Test
  void shouldCollectJobs() {
    // given
    state.getJobState().create(1, new JobRecord());
    state.getJobState().create(2, new JobRecord());
    state.getJobState().create(3, new JobRecord());

    // when

  }

  static final class RecordLengthEvaluator implements Predicate<Integer> {
    private Predicate<Integer> canWriteEventOfLength = length -> true;

    @Override
    public boolean test(final Integer length) {
      return canWriteEventOfLength.test(length);
    }
  }
}
