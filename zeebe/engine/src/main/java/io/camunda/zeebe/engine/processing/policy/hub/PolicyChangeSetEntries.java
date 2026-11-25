/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * A policy change set mapped to the engine's types: the identifiers of its policy update, and its
 * entries in the order the engine applies them.
 *
 * @param header a record with only the identifiers of the update: policy, versions, sequence and
 *     update ID
 * @param entries the entries in apply order, see {@code PolicyUpdateCommandMapper}
 */
@NullMarked
record PolicyChangeSetEntries(PolicyUpdateRecord header, List<Entry> entries) {

  /**
   * A record with the identifiers of the update and every entry; it has no chunk index or count.
   */
  PolicyUpdateRecord toRecord() {
    return chunk(entries);
  }

  /** A record with the identifiers of the update and the given entries, in their order. */
  PolicyUpdateRecord chunk(final List<Entry> chunkEntries) {
    final var sections = new EnumMap<Section, List<Object>>(Section.class);
    chunkEntries.forEach(
        entry ->
            sections.computeIfAbsent(entry.section(), s -> new ArrayList<>()).add(entry.value()));
    final var chunk =
        new PolicyUpdateRecord()
            .setPolicyId(header.getPolicyId())
            .setPolicyVersion(header.getPolicyVersion())
            .setBasePolicyVersion(header.getBasePolicyVersion())
            .setSeqNo(header.getSeqNo())
            .setUpdateId(header.getUpdateId());
    sections.forEach((section, values) -> section.write(chunk, values));
    return chunk;
  }

  /**
   * One entry of a change set.
   *
   * @param section the section of the policy update record the entry belongs to
   * @param value the entry, of the section's record value type
   */
  record Entry(Section section, Object value) {}

  /** The sections of a policy update record, in the order the engine applies them. */
  enum Section {
    TENANTS {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setTenants(cast(values));
      }
    },
    ROLES {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setRoles(cast(values));
      }
    },
    GROUPS {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setGroups(cast(values));
      }
    },
    MAPPING_RULES {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setMappingRules(cast(values));
      }
    },
    MEMBERSHIPS {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setMemberships(cast(values));
      }
    },
    AUTHORIZATIONS {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setAuthorizations(cast(values));
      }
    },
    AUTHORIZATION_DELETES {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setAuthorizationDeletes(cast(values));
      }
    },
    MEMBERSHIP_DELETES {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setMembershipDeletes(cast(values));
      }
    },
    DELETES {
      @Override
      void write(final PolicyUpdateRecord record, final List<?> values) {
        record.setDeletes(cast(values));
      }
    };

    /** Sets the section of the record to the values, which are of the section's value type. */
    abstract void write(PolicyUpdateRecord record, List<?> values);

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(final List<?> values) {
      return (List<T>) values;
    }
  }
}
