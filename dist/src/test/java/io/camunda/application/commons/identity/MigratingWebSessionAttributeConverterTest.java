/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.ConditionalMembership;
import io.camunda.security.api.model.authz.MembershipCondition;
import io.camunda.security.api.model.authz.Operand;
import io.camunda.security.api.model.authz.PrincipalAttribute;
import io.camunda.security.api.model.authz.ResourceAttribute;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigratingWebSessionAttributeConverterTest {

  private final MigratingWebSessionAttributeConverter converter =
      new MigratingWebSessionAttributeConverter();

  @Test
  void shouldDeserializeSessionSerializedWithOldCamundaAuthenticationFqn() throws IOException {
    // given — a payload serialized by the old monorepo class at io.camunda.security.auth
    final var oldAuth =
        new io.camunda.security.auth.CamundaAuthentication(
            "alice",
            null,
            false,
            List.of("group1"),
            List.of("role1"),
            List.of("tenant1"),
            List.of(),
            Map.of("claim", "value"));
    final byte[] payload = serialize(oldAuth);

    // when
    final Object result = converter.deserialize(payload);

    // then — result is the new CSL class, with all fields preserved
    assertThat(result).isInstanceOf(CamundaAuthentication.class);
    final var auth = (CamundaAuthentication) result;
    assertThat(auth.authenticatedUsername()).isEqualTo("alice");
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.anonymousUser()).isFalse();
    assertThat(auth.authenticatedGroupIds()).containsExactly("group1");
    assertThat(auth.authenticatedRoleIds()).containsExactly("role1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("tenant1");
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
    assertThat(auth.claims()).containsEntry("claim", "value");
  }

  @Test
  void shouldRoundTripCurrentCamundaAuthenticationUnchanged() {
    // given — a session attribute serialized by the current CSL class (no migration needed)
    final var auth =
        CamundaAuthentication.of(
            b -> b.user("bob").group("admins").tenant("default").claims(Map.of("sub", "bob")));
    final byte[] payload = converter.serialize(auth);

    // when
    final Object result = converter.deserialize(payload);

    // then
    assertThat(result).isInstanceOf(CamundaAuthentication.class);
    final var restored = (CamundaAuthentication) result;
    assertThat(restored.authenticatedUsername()).isEqualTo("bob");
    assertThat(restored.authenticatedGroupIds()).containsExactly("admins");
    assertThat(restored.authenticatedTenantIds()).containsExactly("default");
    assertThat(restored.claims()).containsEntry("sub", "bob");
  }

  @Test
  void shouldReadASessionStoredBeforeConditionalMemberships() throws IOException {
    // given a session attribute serialized without the conditional memberships field
    final var oldAuth =
        new io.camunda.security.auth.CamundaAuthentication(
            "alice", null, false, List.of(), List.of("role1"), List.of(), List.of(), Map.of());

    // when
    final var restored = (CamundaAuthentication) converter.deserialize(serialize(oldAuth));

    // then
    assertThat(restored.authenticatedRoleIds()).containsExactly("role1");
    assertThat(restored.conditionalRoleMemberships()).isEmpty();
  }

  @Test
  void shouldRoundTripConditionalRoleMemberships() {
    // given both operand variants
    final var reviewerInPaymentsOnOwnTasks =
        new ConditionalMembership(
            "reviewer",
            List.of(
                new MembershipCondition(
                    ResourceAttribute.WORKSPACE, new Operand.Values(Set.of("payments"))),
                new MembershipCondition(
                    ResourceAttribute.ASSIGNEE,
                    new Operand.Principal(PrincipalAttribute.USERNAME))));
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("bob")
                    .role("viewer")
                    .conditionalRoleMemberships(List.of(reviewerInPaymentsOnOwnTasks)));

    // when
    final var restored = (CamundaAuthentication) converter.deserialize(converter.serialize(auth));

    // then
    assertThat(restored).isEqualTo(auth);
    assertThat(restored.conditionalRoleMemberships()).containsExactly(reviewerInPaymentsOnOwnTasks);
  }

  @Test
  void shouldNotReferenceConditionalMembershipsInASessionWithoutThem() {
    // given an authentication as OC builds it: its MembershipPort has no conditional memberships
    final var auth =
        CamundaAuthentication.of(
            b -> b.user("bob").role("viewer").conditionalRoleMembershipsSupplier(List::of));

    // when
    final byte[] payload = converter.serialize(auth);

    // then the payload names no class an older node lacks, so it can still read the session
    assertThat(new String(payload, StandardCharsets.ISO_8859_1))
        .doesNotContain(ConditionalMembership.class.getSimpleName());
  }

  private static byte[] serialize(final Object obj) throws IOException {
    final var baos = new ByteArrayOutputStream();
    try (final var oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
    }
    return baos.toByteArray();
  }
}
