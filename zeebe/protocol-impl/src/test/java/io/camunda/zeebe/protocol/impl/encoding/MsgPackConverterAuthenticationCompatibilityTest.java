/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.ConditionalMembership;
import io.camunda.security.api.model.authz.MembershipCondition;
import io.camunda.security.api.model.authz.Operand;
import io.camunda.security.api.model.authz.PrincipalAttribute;
import io.camunda.security.api.model.authz.ResourceAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class MsgPackConverterAuthenticationCompatibilityTest {

  @Test
  void shouldSerializeCamundaAuthenticationWithSnakeCasePropertyNames() {
    // given
    final var authentication = CamundaAuthentication.none();

    // when
    final var serialized =
        MsgPackConverter.convertToJson(MsgPackConverter.convertToMsgPack(authentication));

    // then
    assertThat(serialized).contains("\"authenticated_username\"");
    assertThat(serialized).contains("\"authenticated_client_id\"");
    assertThat(serialized).contains("\"anonymous_user\"");
    assertThat(serialized).contains("\"authenticated_group_ids\"");
    assertThat(serialized).contains("\"authenticated_role_ids\"");
    assertThat(serialized).contains("\"authenticated_tenant_ids\"");
    assertThat(serialized).contains("\"authenticated_mapping_rule_ids\"");
    assertThat(serialized).doesNotContain("authenticatedUsername");
    assertThat(serialized).doesNotContain("authenticatedMappingRuleIds");
  }

  @Test
  void shouldRoundTripCamundaAuthenticationThroughMsgPack() {
    // given
    final var canonicalJson =
        """
        {
          "anonymous_user": false,
          "authenticated_mapping_rule_ids": ["mapping-1", "mapping-2"]
        }
        """;

    // when
    final var authentication =
        MsgPackConverter.convertToObject(
            new UnsafeBuffer(MsgPackConverter.convertToMsgPack(canonicalJson)),
            CamundaAuthentication.class);
    final var reserialized =
        MsgPackConverter.convertToJson(MsgPackConverter.convertToMsgPack(authentication));

    // then
    assertThat(authentication.authenticatedMappingRuleIds())
        .containsExactly("mapping-1", "mapping-2");
    assertThat(reserialized).contains("\"authenticated_mapping_rule_ids\"");
  }

  @Test
  void shouldMaterializeAllMembershipFieldsWhenSerializingBatchOperationAuthentication() {
    // given — lazy auth whose suppliers track invocation count; this is the batch-op create path
    final var groupCallCount = new AtomicInteger();
    final var roleCallCount = new AtomicInteger();
    final var tenantCallCount = new AtomicInteger();
    final var mappingRuleCallCount = new AtomicInteger();

    final var lazyAuth =
        CamundaAuthentication.of(
            b ->
                b.groupIdsSupplier(
                        () -> {
                          groupCallCount.incrementAndGet();
                          return List.of("g1");
                        })
                    .roleIdsSupplier(
                        () -> {
                          roleCallCount.incrementAndGet();
                          return List.of("r1");
                        })
                    .tenantsSupplier(
                        () -> {
                          tenantCallCount.incrementAndGet();
                          return List.of("t1");
                        })
                    .mappingRulesSupplier(
                        () -> {
                          mappingRuleCallCount.incrementAndGet();
                          return List.of("mr1");
                        }));

    // when — serialization forces resolution of each lazy field (Jackson invokes every getter)
    final var serialized = MsgPackConverter.convertToMsgPack(lazyAuth);

    // then — all four suppliers must have fired
    assertThat(groupCallCount.get()).isEqualTo(1);
    assertThat(roleCallCount.get()).isEqualTo(1);
    assertThat(tenantCallCount.get()).isEqualTo(1);
    assertThat(mappingRuleCallCount.get()).isEqualTo(1);

    // and the membership lists survive the round-trip
    final var deserialized =
        MsgPackConverter.convertToObject(new UnsafeBuffer(serialized), CamundaAuthentication.class);
    assertThat(deserialized.authenticatedGroupIds()).containsExactly("g1");
    assertThat(deserialized.authenticatedRoleIds()).containsExactly("r1");
    assertThat(deserialized.authenticatedTenantIds()).containsExactly("t1");
    assertThat(deserialized.authenticatedMappingRuleIds()).containsExactly("mr1");
  }

  @Test
  void shouldNotPersistConditionalRoleMemberships() throws Exception {
    // given an authentication with conditional memberships of both operand variants
    final var lookups = new AtomicInteger();
    final var authentication =
        CamundaAuthentication.of(
            b ->
                b.user("demo-user")
                    .role("viewer")
                    .conditionalRoleMembershipsSupplier(
                        () -> {
                          lookups.incrementAndGet();
                          return List.of(
                              new ConditionalMembership(
                                  "reviewer",
                                  List.of(
                                      new MembershipCondition(
                                          ResourceAttribute.WORKSPACE,
                                          new Operand.Values(Set.of("payments"))),
                                      new MembershipCondition(
                                          ResourceAttribute.ASSIGNEE,
                                          new Operand.Principal(PrincipalAttribute.USERNAME)))));
                        }));

    // when
    final var serialized = MsgPackConverter.convertToMsgPack(authentication);
    final var json = MsgPackConverter.convertToJson(serialized);
    final var deserialized =
        MsgPackConverter.convertToObject(new UnsafeBuffer(serialized), CamundaAuthentication.class);

    // then the payload keeps exactly the properties older nodes know, so they can still read it
    final var propertyNames = new ArrayList<String>();
    new ObjectMapper().readTree(json).fieldNames().forEachRemaining(propertyNames::add);
    assertThat(propertyNames)
        .containsExactlyInAnyOrder(
            "authenticated_username",
            "authenticated_client_id",
            "anonymous_user",
            "authenticated_group_ids",
            "authenticated_role_ids",
            "authenticated_tenant_ids",
            "authenticated_mapping_rule_ids",
            "claims");
    // and the conditional memberships are neither looked up nor carried
    assertThat(lookups.get()).isZero();
    assertThat(deserialized.authenticatedRoleIds()).containsExactly("viewer");
    assertThat(deserialized.conditionalRoleMemberships()).isEmpty();
  }

  @Test
  void shouldReadABatchOperationAuthenticationPersistedBeforeConditionalMemberships() {
    // given a payload written by a node without conditional memberships
    final var persistedByOldNode =
        """
        {
          "authenticated_username": "demo-user",
          "anonymous_user": false,
          "authenticated_role_ids": ["viewer"]
        }
        """;

    // when
    final var authentication =
        MsgPackConverter.convertToObject(
            new UnsafeBuffer(MsgPackConverter.convertToMsgPack(persistedByOldNode)),
            CamundaAuthentication.class);

    // then
    assertThat(authentication.authenticatedRoleIds()).containsExactly("viewer");
    assertThat(authentication.conditionalRoleMemberships()).isEmpty();
  }
}
