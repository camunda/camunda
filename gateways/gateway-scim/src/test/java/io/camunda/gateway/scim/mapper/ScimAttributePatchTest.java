/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import static io.camunda.gateway.scim.mapper.ScimAttributeMappingTest.ACME;
import static io.camunda.gateway.scim.mapper.ScimAttributeMappingTest.ENTERPRISE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.messages.PatchRequest;
import io.camunda.gateway.scim.ScimAttributeProperties.Attribute;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.mapper.ScimGroupPatchTranslator.GroupPatch;
import io.camunda.gateway.scim.mapper.ScimPatchTranslator.UserPatch;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.UserServices.UserAttribute;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScimAttributePatchTest {

  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final String GROUP_COST_CENTER =
      "urn:ietf:params:scim:schemas:extension:acme:2.0:Group:costCenter";

  private final ScimPatchTranslator translator =
      new ScimPatchTranslator(
          ScimAttributeMapping.of(
              "camunda.scim.user-attributes",
              List.of(
                  new Attribute("department", ENTERPRISE + ":department"),
                  new Attribute("clearance", ACME + ":clearance"),
                  new Attribute("addresses", "addresses")),
              ScimUserMapper.USER_SCHEMA));

  private final ScimGroupPatchTranslator groupTranslator =
      new ScimGroupPatchTranslator(
          new ScimGroupMapper(
              ScimAttributeMapping.of(
                  "camunda.scim.group-attributes",
                  List.of(new Attribute("costCenter", GROUP_COST_CENTER)),
                  ScimGroupMapper.GROUP_SCHEMA)));

  @Test
  void shouldSetAttributeOnReplaceWithExtensionPath() throws Exception {
    // when
    final var patch = translate(op("replace", ENTERPRISE + ":department", "\"R&D\""));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.user().attributes()).containsExactly(Map.entry("department", "R&D"));
  }

  @Test
  void shouldSetAttributeOnAddWithCaseInsensitivePath() throws Exception {
    // when
    final var patch = translate(op("add", ENTERPRISE.toLowerCase() + ":DEPARTMENT", "\"Ops\""));

    // then
    assertThat(patch.user().attributes()).containsEntry("department", "Ops");
  }

  @Test
  void shouldSetAttributesFromPathlessReplaceWithUrnKeys() throws Exception {
    // when
    final var patch =
        translate(
            op(
                "replace",
                null,
                "{\""
                    + ENTERPRISE
                    + ":department\":\"X\",\""
                    + ACME
                    + ":clearance\":2,\"active\":true}"));

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.ATTRIBUTES_MERGE, UserAttribute.ACTIVE);
    assertThat(patch.user().attributes())
        .containsEntry("department", "X")
        .containsEntry("clearance", 2)
        .hasSize(2);
  }

  @Test
  void shouldSetAttributesFromPathlessReplaceWithExtensionObject() throws Exception {
    // when
    final var patch =
        translate(
            op(
                "replace",
                null,
                "{\"" + ENTERPRISE + "\":{\"department\":\"X\",\"division\":\"ignored\"}}"));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.user().attributes()).containsExactly(Map.entry("department", "X"));
  }

  @Test
  void shouldSetAttributesFromPathResolvingToExtensionObject() throws Exception {
    // when
    final var patch = translate(op("replace", ACME, "{\"clearance\":\"top\"}"));

    // then
    assertThat(patch.user().attributes()).containsExactly(Map.entry("clearance", "top"));
  }

  @Test
  void shouldMarkAttributeRemovedWithNullValueOnRemove() throws Exception {
    // when
    final var patch = translate(op("remove", ENTERPRISE + ":department", null));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ATTRIBUTES_MERGE);
    final var expected = new HashMap<String, Object>();
    expected.put("department", null);
    assertThat(patch.user().attributes()).isEqualTo(expected);
  }

  @Test
  void shouldReplaceWholeArrayAttribute() throws Exception {
    // when
    final var patch =
        translate(op("replace", "addresses", "[{\"type\":\"work\",\"locality\":\"Berlin\"}]"));

    // then
    assertThat(patch.user().attributes().get("addresses"))
        .isEqualTo(List.of(Map.of("type", "work", "locality", "Berlin")));
  }

  @Test
  void shouldRejectPathInsideConfiguredAttribute() {
    // when / then
    assertThatThrownBy(
            () -> translate(op("replace", "addresses[type eq \"work\"].locality", "\"Bonn\"")))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidPath"));
  }

  @Test
  void shouldRejectValueFilteredRemoveOfConfiguredAttribute() {
    // when / then
    assertThatThrownBy(() -> translate(op("remove", "addresses[type eq \"work\"]", null)))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidPath"));
  }

  @Test
  void shouldIgnoreTitleWhenNotConfigured() throws Exception {
    // when
    final var patch = translate(op("replace", "title", "\"Dr\""));

    // then
    assertThat(patch.changedAttributes()).isEmpty();
  }

  @Test
  void shouldIgnoreUnconfiguredExtensionAttributes() throws Exception {
    // when
    final var patch = translate(op("replace", ENTERPRISE + ":costCenter", "\"1\""));

    // then
    assertThat(patch.changedAttributes()).isEmpty();
  }

  @Test
  void shouldCombineAttributeAndCoreChangesWithLastOperationWinning() throws Exception {
    // when
    final var patch =
        translate(
            "{\"schemas\":[\"%s\"],\"Operations\":[".formatted(PATCH_SCHEMA)
                + "{\"op\":\"replace\",\"path\":\"displayName\",\"value\":\"Alice\"},"
                + "{\"op\":\"replace\",\"path\":\""
                + ENTERPRISE
                + ":department\",\"value\":\"A\"},"
                + "{\"op\":\"remove\",\"path\":\""
                + ENTERPRISE
                + ":department\"}]}");

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.NAME, UserAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.user().attributes()).containsOnlyKeys("department");
    assertThat(patch.user().attributes().get("department")).isNull();
  }

  @Test
  void shouldRejectChangedAttributesAboveSizeLimit() {
    // when / then
    assertThatThrownBy(
            () ->
                translate(
                    op("replace", ENTERPRISE + ":department", "\"" + "x".repeat(17 * 1024) + "\"")))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldSetGroupAttributeFromExtensionPathAndRemoveIt() throws Exception {
    // when
    final var set = translateGroup(op("replace", GROUP_COST_CENTER, "\"CC-1\""));
    final var removed = translateGroup(op("remove", GROUP_COST_CENTER, null));

    // then
    assertThat(set.changedAttributes()).containsExactly(GroupAttribute.ATTRIBUTES_MERGE);
    assertThat(set.group().attributes()).containsExactly(Map.entry("costCenter", "CC-1"));
    assertThat(removed.group().attributes()).containsOnlyKeys("costCenter");
    assertThat(removed.group().attributes().get("costCenter")).isNull();
  }

  @Test
  void shouldSetGroupAttributeFromPathlessExtensionObject() throws Exception {
    // when
    final var patch =
        translateGroup(
            op(
                "add",
                null,
                "{\"urn:ietf:params:scim:schemas:extension:acme:2.0:Group\":{\"costCenter\":\"CC-2\"}}"));

    // then
    assertThat(patch.group().attributes()).containsExactly(Map.entry("costCenter", "CC-2"));
  }

  @Test
  void shouldIgnoreUnconfiguredGroupAttribute() throws Exception {
    // when
    final var patch = translateGroup(op("replace", "description", "\"x\""));

    // then
    assertThat(patch.changedAttributes()).isEmpty();
    assertThat(patch.memberChanges()).isEmpty();
  }

  @Test
  void shouldApplyConfiguredDisplayNameAndCoreNameOnReplace() throws Exception {
    // given
    final var displayNameTranslator = userTranslatorConfiguringDisplayName();

    // when
    final var patch =
        displayNameTranslator.translate(
            ScimJson.read(op("replace", "displayName", "\"Alice\""), PatchRequest.class), "alice");

    // then
    // the configured attribute and the core name are both updated, not one instead of the other
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.NAME, UserAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.user().attributes()).containsExactly(Map.entry("displayNameAlias", "Alice"));
    assertThat(patch.user().name()).isEqualTo("Alice");
  }

  @Test
  void shouldApplyConfiguredDisplayNameAndCoreNameOnPathlessReplace() throws Exception {
    // given
    final var displayNameTranslator = userTranslatorConfiguringDisplayName();

    // when
    final var patch =
        displayNameTranslator.translate(
            ScimJson.read(op("replace", null, "{\"displayName\":\"Bob\"}"), PatchRequest.class),
            "bob");

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.NAME, UserAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.user().attributes()).containsExactly(Map.entry("displayNameAlias", "Bob"));
    assertThat(patch.user().name()).isEqualTo("Bob");
  }

  @Test
  void shouldApplyConfiguredDisplayNameAndCoreNameOnGroupReplace() throws Exception {
    // given
    final var displayNameGroupTranslator =
        new ScimGroupPatchTranslator(
            new ScimGroupMapper(
                ScimAttributeMapping.of(
                    "camunda.scim.group-attributes",
                    List.of(new Attribute("displayNameAlias", "displayName")),
                    ScimGroupMapper.GROUP_SCHEMA)));

    // when
    final var patch =
        displayNameGroupTranslator.translate(
            ScimJson.read(op("replace", "displayName", "\"Engineering\""), PatchRequest.class),
            "eng");

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(GroupAttribute.NAME, GroupAttribute.ATTRIBUTES_MERGE);
    assertThat(patch.group().attributes())
        .containsExactly(Map.entry("displayNameAlias", "Engineering"));
    assertThat(patch.group().name()).isEqualTo("Engineering");
  }

  private ScimPatchTranslator userTranslatorConfiguringDisplayName() {
    return new ScimPatchTranslator(
        ScimAttributeMapping.of(
            "camunda.scim.user-attributes",
            List.of(new Attribute("displayNameAlias", "displayName")),
            ScimUserMapper.USER_SCHEMA));
  }

  private UserPatch translate(final String json) throws Exception {
    return translator.translate(ScimJson.read(json, PatchRequest.class), "alice");
  }

  private GroupPatch translateGroup(final String json) throws Exception {
    return groupTranslator.translate(ScimJson.read(json, PatchRequest.class), "eng");
  }

  private static String op(final String op, final String path, final String value) {
    return "{\"schemas\":[\"%s\"],\"Operations\":[{\"op\":\"%s\"%s%s}]}"
        .formatted(
            PATCH_SCHEMA,
            op,
            path == null ? "" : ",\"path\":\"" + path.replace("\"", "\\\"") + "\"",
            value == null ? "" : ",\"value\":" + value);
  }
}
