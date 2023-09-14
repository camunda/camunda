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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

public class FormVersionManager {

  private final long initialValue;

  private final ColumnFamily<DbString, FormVersionInfo> formVersionInfoColumnFamily;
  private final DbString formIdKey;
  private final Object2ObjectHashMap<String, FormVersionInfo> versionCache;

  public FormVersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;
    final FormVersionInfo nextVersion = new FormVersionInfo();

    formIdKey = new DbString();
    formVersionInfoColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.FORM_VERSION, transactionContext, formIdKey, nextVersion);
    versionCache = new Object2ObjectHashMap<>();
  }

  public void addFormVersion(final String formId, final long value) {
    formIdKey.wrapString(formId);
    final var versionInfo = getVersionInfo();
    versionInfo.addKnownVersion(value);
    formVersionInfoColumnFamily.upsert(formIdKey, versionInfo);
    versionCache.put(formId, versionInfo);
  }

  public void clear() {
    versionCache.clear();
  }

  /**
   * Returns the latest known version of a form. A form with this version exists in the state.
   *
   * @param formId the form id
   * @return the latest known version of this form
   */
  public long getLatestFormVersion(final DirectBuffer formId) {
    formIdKey.wrapBuffer(formId);
    return getVersionInfo().getLatestVersion();
  }

  private FormVersionInfo getVersionInfo() {
    final var versionInfo =
        versionCache.computeIfAbsent(
            formIdKey.toString(), (key) -> formVersionInfoColumnFamily.get(formIdKey));

    if (versionInfo == null) {
      return new FormVersionInfo().setHighestVersionIfHigher(initialValue);
    }

    return versionInfo;
  }
}
