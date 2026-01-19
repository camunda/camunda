/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.UserWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserExportHandler implements RdbmsExportHandler<UserRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(UserExportHandler.class);

  private final UserWriter userWriter;
  private final HistoryCleanupService historyCleanupService;

  public UserExportHandler(
      final UserWriter userWriter, final HistoryCleanupService historyCleanupService) {
    this.userWriter = userWriter;
    this.historyCleanupService = historyCleanupService;
  }

  @Override
  public boolean canExport(final Record<UserRecordValue> record) {
    // do not react on UserIntent.DELETED to keep historic data
    return record.getValueType() == ValueType.USER
        && (record.getIntent() == UserIntent.CREATED
            || record.getIntent() == UserIntent.UPDATED
            || record.getIntent() == UserIntent.DELETED);
  }

  @Override
  public void export(final Record<UserRecordValue> record) {
    final UserRecordValue value = record.getValue();
    if (record.getIntent() == UserIntent.CREATED) {
      userWriter.create(map(value));
    } else if (record.getIntent() == UserIntent.UPDATED) {
      userWriter.update(map(value));
    } else if (record.getIntent() == UserIntent.DELETED) {
      userWriter.delete(value.getUsername());
      final var endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
      historyCleanupService.scheduleAuditLogsForHistoryCleanup(value.getUsername(), endDate);
    } else {
      LOG.warn("Unexpected intent {} for user record", record.getIntent());
    }
  }

  private UserDbModel map(final UserRecordValue decision) {
    return new UserDbModel.Builder()
        .userKey(decision.getUserKey())
        .username(decision.getUsername())
        .name(decision.getName())
        .email(decision.getEmail())
        .password(decision.getPassword())
        .build();
  }
}
