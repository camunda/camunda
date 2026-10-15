/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.messages.PatchRequest;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator.GroupPatch;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator.MemberChange;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator.MemberChangeType;
import io.camunda.service.GroupServices.GroupAttribute;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScimGroupPatchTranslatorTest {

  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

  private final ScimGroupPatchTranslator translator =
      new ScimGroupPatchTranslator(new ScimGroupMapper());

  @Test
  void shouldTranslateKeycloakPluginReplaceOperations() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[
              {"op":"replace","path":"displayName","value":"Renamed"},
              {"op":"replace","path":"externalId","value":"kc-1"},
              {"op":"replace","path":"members","value":[{"value":"alice"},{"value":"bob"}]}]}
            """);

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID);
    assertThat(patch.group().name()).isEqualTo("Renamed");
    assertThat(patch.group().externalId()).isEqualTo("kc-1");
    assertThat(patch.group().groupId()).isEqualTo("eng");
    assertThat(patch.memberChanges())
        .containsExactly(new MemberChange(MemberChangeType.SET, List.of("alice", "bob")));
  }

  @Test
  void shouldTranslateKeycloakPluginRemoveMembersWithNullValueToEmptySet() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[{"op":"remove","path":"members","value":null}]}
            """);

    // then
    assertThat(patch.changedAttributes()).isEmpty();
    assertThat(patch.memberChanges())
        .containsExactly(new MemberChange(MemberChangeType.SET, List.of()));
  }

  @Test
  void shouldTranslateRemoveMembersWithoutValueToEmptySet() throws Exception {
    // given
    final var patch =
        translate(
            "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"remove\",\"path\":\"members\"}]}");

    // then
    assertThat(patch.memberChanges())
        .containsExactly(new MemberChange(MemberChangeType.SET, List.of()));
  }

  @Test
  void shouldTranslateEntraAddAndFilteredRemoveWithCapitalisedOps() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[
              {"op":"Add","path":"members","value":[{"value":"alice"}]},
              {"op":"Remove","path":"members[value eq \\"bob\\"]"},
              {"op":"Replace","path":"displayName","value":"Eng"}]}
            """);

    // then
    assertThat(patch.changedAttributes()).containsExactly(GroupAttribute.NAME);
    assertThat(patch.memberChanges())
        .containsExactly(
            new MemberChange(MemberChangeType.ADD, List.of("alice")),
            new MemberChange(MemberChangeType.REMOVE, List.of("bob")));
  }

  @Test
  void shouldTranslateEntraRemoveWithValueArray() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[
              {"op":"Remove","path":"members","value":[{"value":"alice"},{"value":"bob"}]}]}
            """);

    // then
    assertThat(patch.memberChanges())
        .containsExactly(new MemberChange(MemberChangeType.REMOVE, List.of("alice", "bob")));
  }

  @Test
  void shouldTranslatePathlessOperationWithObjectValue() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[
              {"op":"add","value":{"displayName":"X","members":[{"value":"alice"}]}}]}
            """);

    // then
    assertThat(patch.changedAttributes()).containsExactly(GroupAttribute.NAME);
    assertThat(patch.memberChanges())
        .containsExactly(new MemberChange(MemberChangeType.ADD, List.of("alice")));
  }

  @Test
  void shouldDropMemberChangesBeforeAReplacement() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],"Operations":[
              {"op":"add","path":"members","value":[{"value":"alice"}]},
              {"op":"replace","path":"members","value":[{"value":"bob"}]},
              {"op":"add","path":"members","value":[{"value":"carol"}]}]}
            """);

    // then
    assertThat(patch.memberChanges())
        .containsExactly(
            new MemberChange(MemberChangeType.SET, List.of("bob")),
            new MemberChange(MemberChangeType.ADD, List.of("carol")));
  }

  @Test
  void shouldClearExternalIdOnRemove() throws Exception {
    // given
    final var patch =
        translate(
            "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"remove\",\"path\":\"externalId\"}]}");

    // then
    assertThat(patch.changedAttributes()).containsExactly(GroupAttribute.EXTERNAL_ID);
    assertThat(patch.group().externalId()).isEmpty();
  }

  @Test
  void shouldRejectNestedGroupMember() {
    // when / then
    assertThatThrownBy(
            () ->
                translate(
                    """
                    {"schemas":["%s"],"Operations":[
                      {"op":"add","path":"members","value":[{"value":"g","type":"Group"}]}]}
                    """))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "replace|members.value|x",
        "replace|members[value eq \\\"a\\\"]|x",
        "replace|displayName.foo|x"
      })
  void shouldRejectUnsupportedPaths(final String op, final String path, final String value) {
    // when / then
    assertThatThrownBy(
            () ->
                translate(
                    "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\""
                        + op
                        + "\",\"path\":\""
                        + path
                        + "\",\"value\":\""
                        + value
                        + "\"}]}"))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidPath"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"description|x", "urn:other:schema:displayName|x", "meta.created|x"})
  void shouldIgnoreUnconfiguredPaths(final String path, final String value) throws Exception {
    // when
    final var patch =
        translate(
            "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"replace\",\"path\":\""
                + path
                + "\",\"value\":\""
                + value
                + "\"}]}");

    // then
    assertThat(patch.changedAttributes()).isEmpty();
    assertThat(patch.memberChanges()).isEmpty();
  }

  @Test
  void shouldRejectRemovingDisplayName() {
    // when / then
    assertThatThrownBy(
            () ->
                translate(
                    "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"remove\",\"path\":\"displayName\"}]}"))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldRejectRemoveWithoutPath() {
    // when / then
    assertThatThrownBy(
            () ->
                translate(
                    "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"remove\",\"value\":\"x\"}]}"))
        .isInstanceOf(BadRequestException.class);
  }

  private GroupPatch translate(final String json) throws Exception {
    return translator.translate(
        ScimJson.read(json.replace("%s", PATCH_SCHEMA), PatchRequest.class), "eng");
  }
}
