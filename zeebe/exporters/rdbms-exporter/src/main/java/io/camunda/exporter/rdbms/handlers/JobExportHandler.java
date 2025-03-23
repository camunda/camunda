/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import io.camunda.db.rdbms.write.service.JobWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Set;

public class JobExportHandler implements RdbmsExportHandler<JobRecordValue> {

  private static final Set<JobIntent> EXPORTABLE_INTENTS =
      Set.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.TIMED_OUT,
          JobIntent.FAILED,
          JobIntent.RETRIES_UPDATED,
          JobIntent.CANCELED,
          JobIntent.ERROR_THROWN,
          JobIntent.MIGRATED);

  private final JobWriter jobWriter;

  public JobExportHandler(final JobWriter jobWriter) {
    this.jobWriter = jobWriter;
  }

  @Override
  public boolean canExport(final Record<JobRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof final JobIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<JobRecordValue> record) {
    if (record.getIntent().equals(JobIntent.CREATED)) {
      jobWriter.create(map(record));
    } else {
      jobWriter.update(map(record));
    }
  }

  private JobDbModel map(final Record<JobRecordValue> record) {
    return new Builder()
        .jobKey(record.getKey())
        .processInstanceKey(record.getValue().getProcessInstanceKey())
        .retries(record.getValue().getRetries())
        .state(record.getIntent().name())
        .partitionId(record.getPartitionId())
        .build();
  }
}
