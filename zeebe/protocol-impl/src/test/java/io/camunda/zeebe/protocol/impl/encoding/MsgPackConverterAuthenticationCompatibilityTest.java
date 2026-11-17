/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
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
}
