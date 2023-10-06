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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

public class DbFormState implements MutableFormState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final DbString tenantIdKey;
  private final DbLong dbFormKey;
  private final DbTenantAwareKey<DbLong> tenantAwareFormKey;
  private final PersistedForm dbPersistedForm;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedForm> formsByKey;
  private final DbString dbFormId;
  private final VersionManager versionManager;
  private final DbLong formVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>> tenantAwareIdAndVersionKey;
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedForm>
      formByIdAndVersionColumnFamily;
  private final Object2ObjectHashMap<String, Object2ObjectHashMap<DirectBuffer, PersistedForm>>
      formByTenantAndIdCache;

  public DbFormState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantIdKey = new DbString();
    dbFormKey = new DbLong();
    tenantAwareFormKey = new DbTenantAwareKey<>(tenantIdKey, dbFormKey, PlacementType.PREFIX);
    dbPersistedForm = new PersistedForm();
    formsByKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORMS, transactionContext, tenantAwareFormKey, dbPersistedForm);

    dbFormId = new DbString();
    formVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(dbFormId, formVersion);
    tenantAwareIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    formByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORM_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareIdAndVersionKey,
            dbPersistedForm);

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.FORM_VERSION, transactionContext);

    formByTenantAndIdCache = new Object2ObjectHashMap<>();
  }

  @Override
  public void storeFormRecord(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormKey.wrapLong(record.getFormKey());
    dbFormId.wrapString(record.getFormId());
    formVersion.wrapLong(record.getVersion());
    dbPersistedForm.wrap(record);
    formsByKey.upsert(tenantAwareFormKey, dbPersistedForm);
    formByIdAndVersionColumnFamily.upsert(tenantAwareIdAndVersionKey, dbPersistedForm);

    updateLatestVersion(record);
    updateLatestFormCache();
  }

  @Override
  public Optional<PersistedForm> findLatestFormById(
      final DirectBuffer formId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    final PersistedForm cachedForm =
        formByTenantAndIdCache.getOrDefault(tenantId, new Object2ObjectHashMap<>()).get(formId);
    if (cachedForm != null) {
      return Optional.of(cachedForm);
    }

    dbFormId.wrapBuffer(formId);
    final long latestVersion = versionManager.getLatestResourceVersion(formId, tenantId);
    formVersion.wrapLong(latestVersion);
    final PersistedForm persistedForm =
        formByIdAndVersionColumnFamily.get(tenantAwareIdAndVersionKey);
    if (persistedForm == null) {
      return Optional.empty();
    }

    final Object2ObjectHashMap<DirectBuffer, PersistedForm> formIdMap =
        formByTenantAndIdCache.computeIfAbsent(
            persistedForm.getTenantId(), id -> new Object2ObjectHashMap<>());
    formIdMap.put(persistedForm.getFormId(), persistedForm);
    return Optional.ofNullable(formByIdAndVersionColumnFamily.get(tenantAwareIdAndVersionKey))
        .map(PersistedForm::copy);
  }

  @Override
  public Optional<PersistedForm> findFormByKey(final long formKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbFormKey.wrapLong(formKey);
    return Optional.ofNullable(formsByKey.get(tenantAwareFormKey)).map(PersistedForm::copy);
  }

  @Override
  public void clearCache() {
    formByTenantAndIdCache.clear();
    versionManager.clear();
  }

  private void updateLatestVersion(final FormRecord formRecord) {
    final var formId = formRecord.getFormId();
    final var version = formRecord.getVersion();
    final var tenantId = formRecord.getTenantId();
    versionManager.addResourceVersion(formId, version, tenantId);
  }

  private void updateLatestFormCache() {
    final Object2ObjectHashMap<DirectBuffer, PersistedForm> formIdMap =
        formByTenantAndIdCache.computeIfAbsent(
            dbPersistedForm.getTenantId(), id -> new Object2ObjectHashMap<>());
    formIdMap.put(dbPersistedForm.getFormId(), dbPersistedForm.copy());
  }
}
