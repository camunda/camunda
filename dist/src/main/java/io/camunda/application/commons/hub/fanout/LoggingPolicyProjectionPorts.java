/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import io.camunda.security.api.model.policy.PolicyChangeSet;
import io.camunda.security.api.model.policy.PolicyDeletions;
import io.camunda.security.api.model.policy.PolicyUpserts;
import io.camunda.security.core.port.out.PolicyProjectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in projection that only logs. The engine-command adapter of the other PoC replaces it by
 * setting {@code camunda.hub.fanout.projection} to anything but {@code logging}.
 */
@Component
@ConditionalOnProperty(
    prefix = "camunda.hub.fanout",
    name = "projection",
    havingValue = "logging",
    matchIfMissing = true)
public class LoggingPolicyProjectionPorts implements PolicyProjectionPorts {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPolicyProjectionPorts.class);

  @Override
  public PolicyProjectionPort forPhysicalTenant(final String physicalTenantId) {
    return changeSet -> LOGGER.info("[pt={}] {}", physicalTenantId, describe(changeSet));
  }

  private static String describe(final PolicyChangeSet changeSet) {
    final PolicyUpserts up = changeSet.upserts();
    final PolicyDeletions del = changeSet.deletions();
    return "PolicyChangeSet v%d %s upserts.roles=%s groups=%s mappingRules=%s authorizations=%d"
            .formatted(
                changeSet.policyVersion(),
                changeSet.kind(),
                up.roles().stream().map(r -> r.roleId()).toList(),
                up.groups().stream().map(g -> g.groupId()).toList(),
                up.mappingRules().stream().map(m -> m.mappingRuleId()).toList(),
                up.authorizations().size())
        + " deletions.roleIds=%s groupIds=%s mappingRuleIds=%s authorizations=%d"
            .formatted(
                del.roleIds(), del.groupIds(), del.mappingRuleIds(), del.authorizations().size());
  }
}
