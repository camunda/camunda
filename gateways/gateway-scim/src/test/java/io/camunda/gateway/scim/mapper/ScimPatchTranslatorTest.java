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
import io.camunda.gateway.scim.mapper.ScimPatchTranslator.UserPatch;
import io.camunda.service.UserServices.UserAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScimPatchTranslatorTest {

  private static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

  private final ScimPatchTranslator translator = new ScimPatchTranslator();

  @Test
  void shouldTranslateEntraDeactivationWithCapitalisedOpAndStringBoolean() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],
             "Operations":[{"op":"Replace","path":"active","value":"False"}]}
            """
                .formatted(PATCH_SCHEMA));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ACTIVE);
    assertThat(patch.user().active()).isFalse();
    assertThat(patch.user().username()).isEqualTo("alice");
  }

  @Test
  void shouldTranslateOktaPathlessReplaceWithObjectValue() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],
             "Operations":[{"op":"replace","value":{"active":false}}]}
            """
                .formatted(PATCH_SCHEMA));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ACTIVE);
    assertThat(patch.user().active()).isFalse();
  }

  @Test
  void shouldTranslatePathlessReplaceWithSeveralAttributes() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],
             "Operations":[{"op":"replace","value":{
               "externalId":"ext-2",
               "displayName":"Alice L.",
               "emails":[{"value":"alice@example.com","type":"work","primary":true}],
               "active":true}}]}
            """
                .formatted(PATCH_SCHEMA));

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(
            UserAttribute.EXTERNAL_ID,
            UserAttribute.NAME,
            UserAttribute.EMAIL,
            UserAttribute.ACTIVE);
    assertThat(patch.user().externalId()).isEqualTo("ext-2");
    assertThat(patch.user().name()).isEqualTo("Alice L.");
    assertThat(patch.user().email()).isEqualTo("alice@example.com");
    assertThat(patch.user().active()).isTrue();
  }

  @Test
  void shouldInterpretKeysOfPathlessValueAsPaths() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],
             "Operations":[{"op":"Replace","value":{
               "name.givenName":"Alice","name.familyName":"Liddell",
               "emails[type eq \\"work\\"].value":"alice@example.com"}}]}
            """
                .formatted(PATCH_SCHEMA));

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.NAME, UserAttribute.EMAIL);
    assertThat(patch.user().name()).isEqualTo("Alice Liddell");
    assertThat(patch.user().email()).isEqualTo("alice@example.com");
  }

  @Test
  void shouldTranslateNestedNameObject() throws Exception {
    // given
    final var patch =
        translate(op("replace", "name", "{\"givenName\":\"Alice\",\"familyName\":\"Liddell\"}"));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.NAME);
    assertThat(patch.user().name()).isEqualTo("Alice Liddell");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "displayName|\"Alice\"|Alice",
        "name.formatted|\"Alice L\"|Alice L",
        "name.givenName|\"Alice\"|Alice",
        "name.familyName|\"Liddell\"|Liddell",
        "DisplayName|\"Alice\"|Alice"
      })
  void shouldMapNamePathsToName(final String path, final String value, final String expected)
      throws Exception {
    // given
    final var patch = translate(op("replace", path, value));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.NAME);
    assertThat(patch.user().name()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "emails|[{\"value\":\"a@x.io\",\"primary\":true}]|a@x.io",
        "emails|[{\"value\":\"first@x.io\"},{\"value\":\"main@x.io\",\"primary\":true}]|main@x.io",
        "emails[type eq \"work\"].value|\"a@x.io\"|a@x.io",
        "emails[primary eq true].value|\"a@x.io\"|a@x.io",
        "emails[type eq \"work\"]|{\"value\":\"a@x.io\"}|a@x.io"
      })
  void shouldMapEmailPathsToEmail(final String path, final String value, final String expected)
      throws Exception {
    // given
    final var patch = translate(op("replace", path, value));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.EMAIL);
    assertThat(patch.user().email()).isEqualTo(expected);
  }

  @Test
  void shouldClearAttributesOnRemove() throws Exception {
    // given
    final var patch =
        translate(
            """
            {"schemas":["%s"],
             "Operations":[{"op":"remove","path":"externalId"},
                           {"op":"remove","path":"emails"}]}
            """
                .formatted(PATCH_SCHEMA));

    // then
    assertThat(patch.changedAttributes())
        .containsExactlyInAnyOrder(UserAttribute.EXTERNAL_ID, UserAttribute.EMAIL);
    assertThat(patch.user().externalId()).isEmpty();
    assertThat(patch.user().email()).isEmpty();
  }

  @Test
  void shouldIgnoreUnchangedUserName() throws Exception {
    // given
    final var patch = translate(op("replace", "userName", "\"alice\""));

    // then
    assertThat(patch.changedAttributes()).isEmpty();
  }

  @Test
  void shouldRejectUserNameChange() {
    // when / then
    assertThatThrownBy(() -> translate(op("replace", "userName", "\"bob\"")))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("mutability"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "name.middleName|\"M\"",
        "emails[type eq \"home\"].value|\"a@x.io\"",
        "emails.type|\"work\"",
        "active.foo|true"
      })
  void shouldRejectUnsupportedPaths(final String path, final String value) {
    // when / then
    assertThatThrownBy(() -> translate(op("replace", path, value)))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidPath"));
  }

  @Test
  void shouldRejectUnsupportedSubPathOfHandledAttributeInPathlessValue() {
    // when / then
    assertThatThrownBy(() -> translate(op("replace", null, "{\"name.middleName\":\"M\"}")))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidPath"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "phoneNumbers|[]",
        "title|\"Dr\"",
        "groups|[]",
        "urn:other:schema:2.0:User:foo|\"x\""
      })
  void shouldIgnoreUnconfiguredPaths(final String path, final String value) throws Exception {
    // when
    final var patch = translate(op("replace", path, value));

    // then
    assertThat(patch.changedAttributes()).isEmpty();
  }

  @Test
  void shouldIgnoreUnconfiguredKeysInPathlessValueButApplyHandledOnes() throws Exception {
    // when
    final var patch = translate(op("replace", null, "{\"title\":\"Dr\",\"active\":false}"));

    // then
    assertThat(patch.changedAttributes()).containsExactly(UserAttribute.ACTIVE);
  }

  @Test
  void shouldRejectNonBooleanActive() {
    // when / then
    assertThatThrownBy(() -> translate(op("replace", "active", "\"maybe\"")))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldRejectRemovingActive() {
    // when / then
    assertThatThrownBy(() -> translate(op("remove", "active", null)))
        .isInstanceOf(BadRequestException.class);
  }

  private UserPatch translate(final String json) throws Exception {
    return translator.translate(ScimJson.read(json, PatchRequest.class), "alice");
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
