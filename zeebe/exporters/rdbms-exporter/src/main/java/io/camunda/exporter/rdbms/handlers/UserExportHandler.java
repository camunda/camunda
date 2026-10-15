/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.service.UserWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserExportHandler implements RdbmsExportHandler<UserRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(UserExportHandler.class);

  private final UserWriter userWriter;

  public UserExportHandler(final UserWriter userWriter) {
    this.userWriter = userWriter;
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
    final OffsetDateTime timestamp = toOffsetDateTime(record.getTimestamp());
    if (record.getIntent() == UserIntent.CREATED) {
      userWriter.create(map(value, timestamp, timestamp));
    } else if (record.getIntent() == UserIntent.UPDATED) {
      // createdAt is passed as null: UPDATE does not write CREATED_AT
      userWriter.update(map(value, null, timestamp));
    } else if (record.getIntent() == UserIntent.DELETED) {
      userWriter.delete(value.getUsername());
    } else {
      LOG.warn("Unexpected intent {} for user record", record.getIntent());
    }
  }

  private UserDbModel map(
      final UserRecordValue decision,
      final OffsetDateTime createdAt,
      final OffsetDateTime lastModifiedAt) {
    return new UserDbModel.Builder()
        .userKey(decision.getUserKey())
        .username(decision.getUsername())
        .name(decision.getName())
        .email(decision.getEmail())
        .password(decision.getPassword())
        .externalId(decision.getExternalId())
        .active(decision.isActive())
        .attributes(AttributesJson.serialize(decision.getAttributes()))
        .createdAt(createdAt)
        .lastModifiedAt(lastModifiedAt)
        .build();
  }
}
