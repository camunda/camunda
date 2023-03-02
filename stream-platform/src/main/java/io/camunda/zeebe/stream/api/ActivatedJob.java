/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Represents an activated {@link io.camunda.zeebe.protocol.record.value.JobRecordValue}. It's
 * expected that the {@link JobRecord#getVariables()} map has been filled out during activation.
 */
public interface ActivatedJob extends BufferWriter {

  /** Returns the unique key of this job */
  long jobKey();

  /** Returns the actual job, with the variables collected during activation. */
  JobRecord record();
}
