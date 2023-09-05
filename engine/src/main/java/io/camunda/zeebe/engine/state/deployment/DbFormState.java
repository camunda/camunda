/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbFormState implements MutableFormState {

  private final DbLong dbFormKey;
  private final PersistedForm dbPersistedForm;
  private final ColumnFamily<DbLong, PersistedForm> formsByKey;
  private final DbString dbFormId;
  private final DbForeignKey<DbLong> fkForm;
  private final ColumnFamily<DbString, DbForeignKey<DbLong>> latestFormKeysByFormId;

  public DbFormState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    dbFormKey = new DbLong();
    dbPersistedForm = new PersistedForm();
    formsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORMS, transactionContext, dbFormKey, dbPersistedForm);

    dbFormId = new DbString();
    fkForm = new DbForeignKey<>(dbFormKey, ZbColumnFamilies.FORMS);
    latestFormKeysByFormId =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LATEST_FORM_KEY_BY_FORM_ID, transactionContext, dbFormId, fkForm);
  }

  @Override
  public void storeFormRecord(final FormRecord record) {
    dbFormKey.wrapLong(record.getFormKey());
    dbPersistedForm.wrap(record);
    formsByKey.upsert(dbFormKey, dbPersistedForm);

    updateLatestFormVersion(record);
  }

  @Override
  public Optional<PersistedForm> findLatestFormById(final DirectBuffer formId) {
    dbFormId.wrapBuffer(formId);

    return Optional.ofNullable(latestFormKeysByFormId.get(dbFormId))
        .flatMap(formKey -> findFormByKey(formKey.inner().getValue()));
  }

  @Override
  public Optional<PersistedForm> findFormByKey(final long formKey) {
    dbFormKey.wrapLong(formKey);
    return Optional.ofNullable(formsByKey.get(dbFormKey)).map(PersistedForm::copy);
  }

  private void updateFormAsLatestVersion(final FormRecord record) {
    dbFormId.wrapBuffer(record.getFormIdBuffer());
    dbFormKey.wrapLong(record.getFormKey());
    latestFormKeysByFormId.update(dbFormId, fkForm);
  }

  private void updateLatestFormVersion(final FormRecord record) {
    findLatestFormById(record.getFormIdBuffer())
        .ifPresentOrElse(
            previousVersion -> {
              if (record.getVersion() > previousVersion.getVersion()) {
                updateFormAsLatestVersion(record);
              }
            },
            () -> insertFormAsLatestVersion(record));
  }

  private void insertFormAsLatestVersion(final FormRecord record) {
    dbFormId.wrapBuffer(record.getFormIdBuffer());
    dbFormKey.wrapLong(record.getFormKey());
    latestFormKeysByFormId.upsert(dbFormId, fkForm);
  }
}
