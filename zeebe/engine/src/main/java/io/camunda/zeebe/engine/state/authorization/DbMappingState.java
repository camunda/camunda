/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.Optional;

public class DbMappingState implements MutableMappingState {

  private final DbString claimName;
  private final DbString claimValue;
  private final DbCompositeKey<DbString, DbString> claim;
  private final PersistedMapping persistedMapping = new PersistedMapping();
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, PersistedMapping>
      mappingColumnFamily;

  private final DbForeignKey<DbCompositeKey<DbString, DbString>> fkClaim;

  private final DbString mappingId;
  private final ColumnFamily<DbString, DbForeignKey<DbCompositeKey<DbString, DbString>>>
      claimByIdColumnFamily;

  public DbMappingState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    claimName = new DbString();
    claimValue = new DbString();
    claim = new DbCompositeKey<>(claimName, claimValue);
    final PersistedMapping persistedMapping = new PersistedMapping();
    mappingColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPINGS, transactionContext, claim, persistedMapping);

    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPINGS);

    mappingId = new DbString();
    claimByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID, transactionContext, mappingId, fkClaim);
  }

  @Override
  public void create(final MappingRecord mappingRecord) {
    final var mappingId = mappingRecord.getMappingId();
    final var name = mappingRecord.getName();
    final var claimName = mappingRecord.getClaimName();
    final var value = mappingRecord.getClaimValue();

    this.mappingId.wrapString(mappingId);
    this.claimName.wrapString(claimName);
    claimValue.wrapString(value);
    persistedMapping.setClaimName(claimName);
    persistedMapping.setClaimValue(value);
    persistedMapping.setName(name);
    persistedMapping.setMappingKey(mappingRecord.getMappingKey());
    persistedMapping.setMappingId(mappingId);

    mappingColumnFamily.insert(claim, persistedMapping);
    claimByIdColumnFamily.insert(this.mappingId, fkClaim);
  }

  @Override
  public void update(final MappingRecord mappingRecord) {
    mappingId.wrapString(mappingRecord.getMappingId());
    get(mappingRecord.getMappingId())
        .ifPresentOrElse(
            persistedMapping -> {
              // remove old record from mapping by claim
              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.deleteExisting(claim);

              persistedMapping.setName(mappingRecord.getName());
              persistedMapping.setClaimName(mappingRecord.getClaimName());
              persistedMapping.setClaimValue(mappingRecord.getClaimValue());

              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.insert(claim, persistedMapping);
              claimByIdColumnFamily.update(mappingId, fkClaim);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to update mapping with id '%s', but a mapping with this id does not exist.",
                      mappingRecord.getMappingId()));
            });
  }

  @Override
  public void delete(final String id) {
    get(id)
        .ifPresentOrElse(
            persistedMapping -> {
              mappingId.wrapString(persistedMapping.getMappingId());
              claimName.wrapString(persistedMapping.getClaimName());
              claimValue.wrapString(persistedMapping.getClaimValue());
              mappingColumnFamily.deleteExisting(claim);
              claimByIdColumnFamily.deleteExisting(mappingId);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to delete mapping with id '%s', but a mapping with this id does not exist.",
                      id));
            });
  }

  @Override
  public Optional<PersistedMapping> get(final String id) {
    mappingId.wrapString(id);
    final var fk = claimByIdColumnFamily.get(mappingId);
    if (fk != null) {
      return Optional.of(mappingColumnFamily.get(fk.inner()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<PersistedMapping> get(final String claimName, final String claimValue) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    final var persistedMapping = mappingColumnFamily.get(claim);

    if (persistedMapping == null) {
      return Optional.empty();
    }

    return Optional.of(persistedMapping.copy());
  }
}
