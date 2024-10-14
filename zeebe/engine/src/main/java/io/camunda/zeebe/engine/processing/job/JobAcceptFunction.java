/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@FunctionalInterface
public interface JobAcceptFunction {

  void accept(
      final TypedRecord<JobRecord> record,
      final CommandControl<JobRecord> commandControl,
      final JobRecord jobRecord);
}
