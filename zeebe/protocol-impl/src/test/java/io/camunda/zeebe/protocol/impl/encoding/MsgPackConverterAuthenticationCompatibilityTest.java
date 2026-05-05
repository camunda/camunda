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
}
