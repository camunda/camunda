/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

public class DbFormState implements MutableFormState {

  private static final int DEFAULT_VERSION_VALUE = 0;

  private final DbString tenantIdKey;
  private final DbLong dbFormKey;
  private final DbTenantAwareKey<DbLong> tenantAwareFormKey;
  private final DbForeignKey<DbTenantAwareKey<DbLong>> fkFormKey;
  private final PersistedForm dbPersistedForm;
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedForm> formsByKey;
  private final DbString dbFormId;
  private final VersionManager versionManager;

  private final DbLong formVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>> tenantAwareIdAndVersionKey;
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedForm>
      formByIdAndVersionColumnFamily;

  private final DbLong dbDeploymentKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareFormIdAndDeploymentKey;

  /**
   * <b>Note</b>: Will only be filled with entries deployed from 8.6 onwards; previously deployed
   * forms will not have an entry in this column family.
   */
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      formKeyByFormIdAndDeploymentKeyColumnFamily;

  private final DbString dbVersionTag;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareFormIdAndVersionTagKey;

  /**
   * <b>Note</b>: Will only be filled with forms deployed from 8.6 onwards that have a version tag
   * assigned (which is an optional property).
   */
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbString>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      formKeyByFormIdAndVersionTagColumnFamily;

  private final Cache<TenantIdAndFormId, PersistedForm> formsByTenantIdAndIdCache;

  public DbFormState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final EngineConfiguration config) {
    tenantIdKey = new DbString();
    dbFormKey = new DbLong();
    tenantAwareFormKey = new DbTenantAwareKey<>(tenantIdKey, dbFormKey, PlacementType.PREFIX);
    fkFormKey = new DbForeignKey<>(tenantAwareFormKey, ZbColumnFamilies.FORMS);
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

    dbDeploymentKey = new DbLong();
    tenantAwareFormIdAndDeploymentKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbFormId, dbDeploymentKey), PlacementType.PREFIX);
    formKeyByFormIdAndDeploymentKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORM_KEY_BY_FORM_ID_AND_DEPLOYMENT_KEY,
            transactionContext,
            tenantAwareFormIdAndDeploymentKey,
            fkFormKey);

    dbVersionTag = new DbString();
    tenantAwareFormIdAndVersionTagKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(dbFormId, dbVersionTag), PlacementType.PREFIX);
    formKeyByFormIdAndVersionTagColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORM_KEY_BY_FORM_ID_AND_VERSION_TAG,
            transactionContext,
            tenantAwareFormIdAndVersionTagKey,
            fkFormKey);

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.FORM_VERSION, transactionContext);

    formsByTenantIdAndIdCache =
        CacheBuilder.newBuilder().maximumSize(config.getFormCacheCapacity()).build();
  }

  @Override
  public void storeFormInFormColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormKey.wrapLong(record.getFormKey());
    dbPersistedForm.wrap(record);
    formsByKey.upsert(tenantAwareFormKey, dbPersistedForm);
    formsByTenantIdAndIdCache.put(
        new TenantIdAndFormId(record.getTenantId(), record.getFormId()), dbPersistedForm.copy());
  }

  @Override
  public void storeFormInFormByIdAndVersionColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormId.wrapString(record.getFormId());
    formVersion.wrapLong(record.getVersion());
    dbPersistedForm.wrap(record);
    formByIdAndVersionColumnFamily.upsert(tenantAwareIdAndVersionKey, dbPersistedForm);
  }

  @Override
  public void storeFormInFormKeyByFormIdAndDeploymentKeyColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormKey.wrapLong(record.getFormKey());
    dbFormId.wrapString(record.getFormId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    formKeyByFormIdAndDeploymentKeyColumnFamily.upsert(
        tenantAwareFormIdAndDeploymentKey, fkFormKey);
  }

  @Override
  public void storeFormInFormKeyByFormIdAndVersionTagColumnFamily(final FormRecord record) {
    final var versionTag = record.getVersionTag();
    if (!versionTag.isBlank()) {
      tenantIdKey.wrapString(record.getTenantId());
      dbFormKey.wrapLong(record.getFormKey());
      dbFormId.wrapString(record.getFormId());
      dbVersionTag.wrapString(versionTag);
      formKeyByFormIdAndVersionTagColumnFamily.upsert(tenantAwareFormIdAndVersionTagKey, fkFormKey);
    }
  }

  @Override
  public void updateLatestVersion(final FormRecord record) {
    versionManager.addResourceVersion(
        record.getFormId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteFormInFormsColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormKey.wrapLong(record.getFormKey());
    formsByKey.deleteExisting(tenantAwareFormKey);
    formsByTenantIdAndIdCache.invalidate(
        new TenantIdAndFormId(record.getTenantId(), record.getFormId()));
  }

  @Override
  public void deleteFormInFormByIdAndVersionColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormId.wrapString(record.getFormId());
    formVersion.wrapLong(record.getVersion());
    formByIdAndVersionColumnFamily.deleteExisting(tenantAwareIdAndVersionKey);
  }

  @Override
  public void deleteFormInFormVersionColumnFamily(final FormRecord record) {
    versionManager.deleteResourceVersion(
        record.getFormId(), record.getVersion(), record.getTenantId());
  }

  @Override
  public void deleteFormInFormKeyByFormIdAndDeploymentKeyColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormId.wrapString(record.getFormId());
    dbDeploymentKey.wrapLong(record.getDeploymentKey());
    formKeyByFormIdAndDeploymentKeyColumnFamily.deleteIfExists(tenantAwareFormIdAndDeploymentKey);
  }

  @Override
  public void deleteFormInFormKeyByFormIdAndVersionTagColumnFamily(final FormRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    dbFormId.wrapString(record.getFormId());
    dbVersionTag.wrapString(record.getVersionTag());
    formKeyByFormIdAndVersionTagColumnFamily.deleteIfExists(tenantAwareFormIdAndVersionTagKey);
  }

  @Override
  public void setMissingDeploymentKey(
      final String tenantId, final long formKey, final long deploymentKey) {
    tenantIdKey.wrapString(tenantId);
    dbFormKey.wrapLong(formKey);
    dbDeploymentKey.wrapLong(deploymentKey);

    final var form = formsByKey.get(tenantAwareFormKey);
    if (form.getDeploymentKey() == deploymentKey) {
      return;
    }

    if (form.hasDeploymentKey()) {
      throw new IllegalStateException(
          String.format(
              "Expected to set deployment key '%d' on form with key '%d', but form already has deployment key '%d'.",
              deploymentKey, formKey, form.getDeploymentKey()));
    }
    formVersion.wrapLong(form.getVersion());

    form.setDeploymentKey(deploymentKey);
    formsByKey.update(tenantAwareFormKey, form);
    formByIdAndVersionColumnFamily.update(tenantAwareIdAndVersionKey, form);
    formKeyByFormIdAndDeploymentKeyColumnFamily.insert(
        tenantAwareFormIdAndDeploymentKey, fkFormKey);

    final var dbKey = new TenantIdAndFormId(tenantId, BufferUtil.bufferAsString(form.getFormId()));
    final var formInCache = formsByTenantIdAndIdCache.getIfPresent(dbKey);
    if (formInCache == null || formInCache.getVersion() <= form.getVersion()) {
      final var copyForCache = form.copy();
      formsByTenantIdAndIdCache.put(dbKey, copyForCache);
    }
  }

  @Override
  public Optional<PersistedForm> findLatestFormById(final String formId, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    final Optional<PersistedForm> cachedForm = getFormFromCache(tenantId, formId);
    if (cachedForm.isPresent()) {
      return cachedForm;
    }

    final PersistedForm persistedForm = getPersistedFormById(formId, tenantId);
    if (persistedForm == null) {
      return Optional.empty();
    }
    formsByTenantIdAndIdCache.put(new TenantIdAndFormId(tenantId, formId), persistedForm);
    return Optional.of(persistedForm);
  }

  @Override
  public Optional<PersistedForm> findFormByKey(final long formKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbFormKey.wrapLong(formKey);
    return Optional.ofNullable(formsByKey.get(tenantAwareFormKey)).map(PersistedForm::copy);
  }

  @Override
  public Optional<PersistedForm> findFormByIdAndDeploymentKey(
      final String formId, final long deploymentKey, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbFormId.wrapString(formId);
    dbDeploymentKey.wrapLong(deploymentKey);
    return Optional.ofNullable(
            formKeyByFormIdAndDeploymentKeyColumnFamily.get(tenantAwareFormIdAndDeploymentKey))
        .flatMap(key -> findFormByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public Optional<PersistedForm> findFormByIdAndVersionTag(
      final String formId, final String versionTag, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    dbFormId.wrapString(formId);
    dbVersionTag.wrapString(versionTag);
    return Optional.ofNullable(
            formKeyByFormIdAndVersionTagColumnFamily.get(tenantAwareFormIdAndVersionTagKey))
        .flatMap(key -> findFormByKey(key.inner().wrappedKey().getValue(), tenantId));
  }

  @Override
  public void forEachForm(final FormIdentifier previousForm, final PersistedFormVisitor visitor) {
    if (previousForm == null) {
      formsByKey.whileTrue((key, value) -> visitor.visit(value));
      return;
    }

    tenantIdKey.wrapString(previousForm.tenantId());
    dbFormKey.wrapLong(previousForm.key());
    formsByKey.whileTrue(
        tenantAwareFormKey,
        (key, value) -> {
          if (key.tenantKey().toString().equals(previousForm.tenantId())
              && key.wrappedKey().getValue() == previousForm.key()) {
            return true;
          }
          return visitor.visit(value);
        });
  }

  @Override
  public int getNextFormVersion(final String formId, final String tenantId) {
    return (int) versionManager.getHighestResourceVersion(formId, tenantId) + 1;
  }

  @Override
  public void clearCache() {
    formsByTenantIdAndIdCache.invalidateAll();
    versionManager.clear();
  }

  private PersistedForm getPersistedFormById(final String formId, final String tenantId) {
    dbFormId.wrapString(formId);
    final long latestVersion = versionManager.getLatestResourceVersion(formId, tenantId);
    formVersion.wrapLong(latestVersion);
    final PersistedForm persistedForm =
        formByIdAndVersionColumnFamily.get(tenantAwareIdAndVersionKey);
    if (persistedForm == null) {
      return null;
    }
    return persistedForm.copy();
  }

  private Optional<PersistedForm> getFormFromCache(final String tenantId, final String formId) {
    return Optional.ofNullable(
        formsByTenantIdAndIdCache.getIfPresent(new TenantIdAndFormId(tenantId, formId)));
  }

  private record TenantIdAndFormId(String tenantId, String formId) {}
}
