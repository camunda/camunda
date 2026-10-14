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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Stores every version of a mapping rule in two column families.
 *
 * <p>The claim family is keyed by (claimName, claimValue, policyVersion). A rule that moves to
 * another claim leaves a tombstone on the old claim at the new version, so a claim lookup finds
 * exactly the rule that holds the claim at the visible version. The ID family is keyed by
 * (mappingRuleId, policyVersion) and points to the claim row of that version, which is a tombstone
 * for a deleted rule.
 */
public class DbMappingRuleState implements MutableMappingRuleState {

  private final DbString claimName = new DbString();
  private final DbString claimValue = new DbString();
  private final DbLong policyVersion = new DbLong();
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong> claim =
      new DbCompositeKey<>(new DbCompositeKey<>(claimName, claimValue), policyVersion);
  private final PersistedMappingRule persistedMappingRule = new PersistedMappingRule();
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, PersistedMappingRule>
      mappingRuleColumnFamily;

  private final DbString mappingRuleId = new DbString();
  private final DbCompositeKey<DbString, DbLong> idKey =
      new DbCompositeKey<>(mappingRuleId, policyVersion);
  private final DbForeignKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>> fkClaim;
  private final ColumnFamily<
          DbCompositeKey<DbString, DbLong>,
          DbForeignKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>>>
      claimByIdColumnFamily;

  private final LongSupplier currentVersion;

  public DbMappingRuleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    mappingRuleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPING_RULES, transactionContext, claim, new PersistedMappingRule());

    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPING_RULES);
    claimByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID, transactionContext, idKey, fkClaim);
  }

  @Override
  public void create(final MappingRuleRecord mappingRuleRecord) {
    putClaimRow(
        mappingRuleRecord.getMappingRuleKey(),
        mappingRuleRecord.getMappingRuleId(),
        mappingRuleRecord.getName(),
        mappingRuleRecord.getClaimName(),
        mappingRuleRecord.getClaimValue(),
        mappingRuleRecord.getPolicyVersion(),
        false);
    putIdRow(mappingRuleRecord.getMappingRuleId(), mappingRuleRecord.getPolicyVersion());
  }

  @Override
  public void update(final MappingRuleRecord mappingRuleRecord) {
    final var version = mappingRuleRecord.getPolicyVersion();
    final var existing = getExisting(mappingRuleRecord.getMappingRuleId(), version, "update");
    if (!existing.getClaimName().equals(mappingRuleRecord.getClaimName())
        || !existing.getClaimValue().equals(mappingRuleRecord.getClaimValue())) {
      // the old claim is released at this version
      putClaimRow(
          existing.getMappingRuleKey(),
          existing.getMappingRuleId(),
          existing.getName(),
          existing.getClaimName(),
          existing.getClaimValue(),
          version,
          true);
    }
    putClaimRow(
        existing.getMappingRuleKey(),
        mappingRuleRecord.getMappingRuleId(),
        mappingRuleRecord.getName(),
        mappingRuleRecord.getClaimName(),
        mappingRuleRecord.getClaimValue(),
        version,
        false);
    putIdRow(mappingRuleRecord.getMappingRuleId(), version);
  }

  @Override
  public void delete(final MappingRuleRecord mappingRuleRecord) {
    final var version = mappingRuleRecord.getPolicyVersion();
    final var existing = getExisting(mappingRuleRecord.getMappingRuleId(), version, "delete");
    putClaimRow(
        existing.getMappingRuleKey(),
        existing.getMappingRuleId(),
        existing.getName(),
        existing.getClaimName(),
        existing.getClaimValue(),
        version,
        true);
    putIdRow(existing.getMappingRuleId(), version);
  }

  @Override
  public Optional<PersistedMappingRule> get(final String id, final long commandVersion) {
    final long visible = visibleVersion(commandVersion);
    this.mappingRuleId.wrapString(id);
    policyVersion.wrapLong(visible);
    final var found = new Object[3];
    claimByIdColumnFamily.whileTrueReverse(
        idKey,
        (key, fk) -> {
          if (!key.first().toString().equals(id)) {
            return false;
          }
          final long entryVersion = key.second().getValue();
          if (entryVersion > visible) {
            return true;
          }
          // copy the claim out of the foreign key before the column family is read again
          final var target = fk.inner();
          found[0] = target.first().first().toString();
          found[1] = target.first().second().toString();
          found[2] = entryVersion;
          return false;
        });
    if (found[0] == null) {
      return Optional.empty();
    }
    return rowAt((String) found[0], (String) found[1], (long) found[2]);
  }

  @Override
  public Optional<PersistedMappingRule> get(
      final String claimName, final String claimValue, final long commandVersion) {
    final long visible = visibleVersion(commandVersion);
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    policyVersion.wrapLong(visible);
    final var found = new PersistedMappingRule[1];
    mappingRuleColumnFamily.whileTrueReverse(
        claim,
        (key, value) -> {
          if (!value.getClaimName().equals(claimName)
              || !value.getClaimValue().equals(claimValue)) {
            return false;
          }
          if (key.second().getValue() > visible) {
            return true;
          }
          if (!value.isDeleted()) {
            found[0] = copyOf(value);
          }
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  @Override
  public Collection<PersistedMappingRule> getAll() {
    final long visible = currentVersion.getAsLong();
    final Map<String, PersistedMappingRule> latest = new HashMap<>();
    final Map<String, Long> latestVersion = new HashMap<>();
    mappingRuleColumnFamily.whileTrue(
        (key, value) -> {
          final long entryVersion = key.second().getValue();
          final var claimKey = value.getClaimName() + '\u0000' + value.getClaimValue();
          if (entryVersion <= visible && entryVersion > latestVersion.getOrDefault(claimKey, -1L)) {
            latestVersion.put(claimKey, entryVersion);
            latest.put(claimKey, copyOf(value));
          }
          return true;
        });
    final var mappingRules = new LinkedList<PersistedMappingRule>();
    latest.values().stream().filter(rule -> !rule.isDeleted()).forEach(mappingRules::add);
    return mappingRules;
  }

  private void putClaimRow(
      final long mappingRuleKey,
      final String id,
      final String name,
      final String claimName,
      final String claimValue,
      final long version,
      final boolean deleted) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    policyVersion.wrapLong(version);
    persistedMappingRule
        .setMappingRuleKey(mappingRuleKey)
        .setMappingRuleId(id)
        .setName(name)
        .setClaimName(claimName)
        .setClaimValue(claimValue)
        .setDeleted(deleted);
    mappingRuleColumnFamily.upsert(claim, persistedMappingRule);
  }

  /** Points the ID row of this version at the claim row written last. */
  private void putIdRow(final String id, final long version) {
    mappingRuleId.wrapString(id);
    policyVersion.wrapLong(version);
    claimByIdColumnFamily.upsert(idKey, fkClaim);
  }

  private PersistedMappingRule getExisting(
      final String id, final long version, final String action) {
    return get(id, version)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Expected to %s mapping rule with id '%s', but a mapping rule with this id does not exist.",
                        action, id)));
  }

  private Optional<PersistedMappingRule> rowAt(
      final String claimName, final String claimValue, final long version) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    policyVersion.wrapLong(version);
    final var row = mappingRuleColumnFamily.get(claim, PersistedMappingRule::new);
    if (row == null || row.isDeleted()) {
      return Optional.empty();
    }
    return Optional.of(row);
  }

  private long visibleVersion(final long commandVersion) {
    return Math.max(currentVersion.getAsLong(), commandVersion);
  }

  private static PersistedMappingRule copyOf(final PersistedMappingRule rule) {
    final var copy = new PersistedMappingRule();
    copy.copyFrom(rule);
    return copy;
  }
}
