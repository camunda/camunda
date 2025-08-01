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
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;

public class DbMappingRuleState implements MutableMappingRuleState {

  private final DbString claimName;
  private final DbString claimValue;
  private final DbCompositeKey<DbString, DbString> claim;
  private final PersistedMappingRule persistedMappingRule = new PersistedMappingRule();
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, PersistedMappingRule>
      mappingRuleColumnFamily;

  private final DbForeignKey<DbCompositeKey<DbString, DbString>> fkClaim;

  private final DbString mappingRuleId;
  private final ColumnFamily<DbString, DbForeignKey<DbCompositeKey<DbString, DbString>>>
      claimByIdColumnFamily;

  public DbMappingRuleState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    claimName = new DbString();
    claimValue = new DbString();
    claim = new DbCompositeKey<>(claimName, claimValue);
    final PersistedMappingRule persistedMappingRule = new PersistedMappingRule();
    mappingRuleColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MAPPING_RULES, transactionContext, claim, persistedMappingRule);

    fkClaim = new DbForeignKey<>(claim, ZbColumnFamilies.MAPPING_RULES);

    mappingRuleId = new DbString();
    claimByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CLAIM_BY_ID, transactionContext, mappingRuleId, fkClaim);
  }

  @Override
  public void create(final MappingRuleRecord mappingRuleRecord) {
    final var mappingRuleId = mappingRuleRecord.getMappingRuleId();
    final var name = mappingRuleRecord.getName();
    final var claimName = mappingRuleRecord.getClaimName();
    final var value = mappingRuleRecord.getClaimValue();

    this.mappingRuleId.wrapString(mappingRuleId);
    this.claimName.wrapString(claimName);
    claimValue.wrapString(value);
    persistedMappingRule.setClaimName(claimName);
    persistedMappingRule.setClaimValue(value);
    persistedMappingRule.setName(name);
    persistedMappingRule.setMappingRuleKey(mappingRuleRecord.getMappingRuleKey());
    persistedMappingRule.setMappingRuleId(mappingRuleId);

    mappingRuleColumnFamily.insert(claim, persistedMappingRule);
    claimByIdColumnFamily.insert(this.mappingRuleId, fkClaim);
  }

  @Override
  public void update(final MappingRuleRecord mappingRuleRecord) {
    mappingRuleId.wrapString(mappingRuleRecord.getMappingRuleId());
    get(mappingRuleRecord.getMappingRuleId())
        .ifPresentOrElse(
            persistedMappingRule -> {
              // remove old record from mapping rule by claim
              claimName.wrapString(persistedMappingRule.getClaimName());
              claimValue.wrapString(persistedMappingRule.getClaimValue());
              mappingRuleColumnFamily.deleteExisting(claim);

              persistedMappingRule.setName(mappingRuleRecord.getName());
              persistedMappingRule.setClaimName(mappingRuleRecord.getClaimName());
              persistedMappingRule.setClaimValue(mappingRuleRecord.getClaimValue());

              claimName.wrapString(persistedMappingRule.getClaimName());
              claimValue.wrapString(persistedMappingRule.getClaimValue());
              mappingRuleColumnFamily.insert(claim, persistedMappingRule);
              claimByIdColumnFamily.update(mappingRuleId, fkClaim);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to update mapping rule with id '%s', but a mapping rule with this id does not exist.",
                      mappingRuleRecord.getMappingRuleId()));
            });
  }

  @Override
  public void delete(final String id) {
    get(id)
        .ifPresentOrElse(
            persistedMappingRule -> {
              mappingRuleId.wrapString(persistedMappingRule.getMappingRuleId());
              claimName.wrapString(persistedMappingRule.getClaimName());
              claimValue.wrapString(persistedMappingRule.getClaimValue());
              mappingRuleColumnFamily.deleteExisting(claim);
              claimByIdColumnFamily.deleteExisting(mappingRuleId);
            },
            () -> {
              throw new IllegalStateException(
                  String.format(
                      "Expected to delete mapping rule with id '%s', but a mapping rule with this id does not exist.",
                      id));
            });
  }

  @Override
  public Optional<PersistedMappingRule> get(final String id) {
    mappingRuleId.wrapString(id);
    final var fk = claimByIdColumnFamily.get(mappingRuleId);
    if (fk != null) {
      return Optional.of(mappingRuleColumnFamily.get(fk.inner()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<PersistedMappingRule> get(final String claimName, final String claimValue) {
    this.claimName.wrapString(claimName);
    this.claimValue.wrapString(claimValue);
    final var persistedMappingRule = mappingRuleColumnFamily.get(claim, PersistedMappingRule::new);

    if (persistedMappingRule == null) {
      return Optional.empty();
    }

    return Optional.of(persistedMappingRule);
  }

  @Override
  public Collection<PersistedMappingRule> getAll() {
    final var mappingRules = new LinkedList<PersistedMappingRule>();
    mappingRuleColumnFamily.forEach(mappingRule -> mappingRules.add(mappingRule.copy()));
    return mappingRules;
  }
}
