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

import com.unboundid.scim2.common.Path;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import io.camunda.gateway.scim.ScimAttributeProperties.Attribute;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping.MatchKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class ScimAttributeMappingTest {

  static final String ENTERPRISE = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
  static final String ACME = "urn:ietf:params:scim:schemas:extension:acme:2.0:User";

  private static ScimAttributeMapping mapping(final Attribute... attributes) {
    return ScimAttributeMapping.of(
        "camunda.scim.user-attributes", List.of(attributes), ScimUserMapper.USER_SCHEMA);
  }

  private static ScimAttributeMapping userMapping() {
    return mapping(
        new Attribute("department", ENTERPRISE + ":department"),
        new Attribute("clearance", ACME + ":clearance"),
        new Attribute("title", "title"),
        new Attribute("addresses", "addresses"),
        new Attribute("givenName", "name.givenName"));
  }

  private static ObjectNode json(final String json) throws Exception {
    return ScimJson.read(json, ObjectNode.class);
  }

  @Test
  void shouldBeEmptyByDefault() {
    // when
    final var mapping = ScimAttributeMapping.of("p", null, ScimUserMapper.USER_SCHEMA);

    // then
    assertThat(mapping.isEmpty()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "1abc", "a-b", "a b", "_a"})
  void shouldRejectInvalidName(final String name) {
    // when / then
    assertThatThrownBy(() -> mapping(new Attribute(name, "title")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("camunda.scim.user-attributes[0].name");
  }

  @Test
  void shouldRejectMissingPath() {
    // when / then
    assertThatThrownBy(() -> mapping(new Attribute("a", " ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[0].path is required");
  }

  @Test
  void shouldRejectDuplicateName() {
    // when / then
    assertThatThrownBy(() -> mapping(new Attribute("a", "title"), new Attribute("a", "nickName")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[1].name 'a' is not unique");
  }

  @Test
  void shouldRejectDuplicatePathIgnoringCaseAndCoreUrn() {
    // when / then
    assertThatThrownBy(
            () ->
                mapping(
                    new Attribute("a", "title"),
                    new Attribute("b", ScimUserMapper.USER_SCHEMA + ":Title")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[1].path")
        .hasMessageContaining("is not unique");
  }

  @ParameterizedTest
  @ValueSource(strings = {"addresses[type eq \"work\"]", "addresses[type eq \"work\"].locality"})
  void shouldRejectValueFilterInPath(final String path) {
    // when / then
    assertThatThrownBy(() -> mapping(new Attribute("a", path)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value filter");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a[", "a..b", "a[type eq]"})
  void shouldRejectUnparsablePath(final String path) {
    // when / then
    assertThatThrownBy(() -> mapping(new Attribute("a", path)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[0].path");
  }

  @Test
  void shouldExtractConfiguredAttributesInScimShape() throws Exception {
    // given
    final var resource =
        json(
            """
            {"userName":"alice","title":"Dr",
             "addresses":[{"type":"work","locality":"Berlin"}],
             "name":{"givenName":"Alice","familyName":"L"},
             "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User":{"department":"R&D","division":"x"},
             "urn:ietf:params:scim:schemas:extension:acme:2.0:User":{"clearance":3}}
            """);

    // when
    final var extracted = userMapping().extract(resource);

    // then
    assertThat(extracted)
        .containsEntry("department", "R&D")
        .containsEntry("clearance", 3)
        .containsEntry("title", "Dr")
        .containsEntry("givenName", "Alice")
        .containsEntry("addresses", List.of(Map.of("type", "work", "locality", "Berlin")))
        .hasSize(5);
  }

  @Test
  void shouldNotIncludeAbsentAttributes() throws Exception {
    // when
    final var extracted = userMapping().extract(json("{\"userName\":\"alice\",\"title\":null}"));

    // then
    assertThat(extracted).isEmpty();
  }

  @Test
  void shouldFindExtensionCaseInsensitively() throws Exception {
    // when
    final var extracted =
        userMapping()
            .extract(
                json(
                    "{\"URN:ietf:params:scim:schemas:extension:enterprise:2.0:User\":{\"Department\":\"X\"}}"));

    // then
    assertThat(extracted).containsExactly(Map.entry("department", "X"));
  }

  @Test
  void shouldRejectAttributesAboveSizeLimit() {
    // given
    final var resource = ScimJson.newObject().put("title", "x".repeat(17 * 1024));

    // when / then
    assertThatThrownBy(() -> userMapping().extract(resource))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldWriteStoredAttributesAtConfiguredPathsAndAddExtensionSchemas() throws Exception {
    // given
    final var resource = json("{\"schemas\":[\"" + ScimUserMapper.USER_SCHEMA + "\"]}");

    // when
    userMapping()
        .writeTo(
            resource,
            Map.of("department", "R&D", "clearance", 3, "title", "Dr", "givenName", "Alice"));

    // then
    assertThat(resource.get("title").asString()).isEqualTo("Dr");
    assertThat(resource.get("name").get("givenName").asString()).isEqualTo("Alice");
    assertThat(resource.get(ENTERPRISE).get("department").asString()).isEqualTo("R&D");
    assertThat(resource.get(ACME).get("clearance").asInt()).isEqualTo(3);
    assertThat(resource.get("schemas").valueStream().map(n -> n.asString()))
        .containsExactly(ScimUserMapper.USER_SCHEMA, ENTERPRISE, ACME);
  }

  @Test
  void shouldSkipStoredAttributesThatAreNoLongerConfigured() throws Exception {
    // given
    final var resource = json("{}");

    // when
    userMapping().writeTo(resource, Map.of("legacy", "x"));

    // then
    assertThat(resource.isEmpty()).isTrue();
  }

  @Test
  void shouldClassifyPatchPaths() throws Exception {
    // given
    final var mapping = userMapping();

    // then
    assertThat(
            mapping
                .match(
                    Path.fromString(ENTERPRISE.replace("enterprise", "ENTERPRISE") + ":department"))
                .kind())
        .isEqualTo(MatchKind.EXACT);
    assertThat(mapping.match(Path.fromString(ENTERPRISE + ":DEPARTMENT")).kind())
        .isEqualTo(MatchKind.EXACT);
    assertThat(
            mapping
                .match(Path.fromString("urn:ietf:params:scim:schemas:core:2.0:User:title"))
                .kind())
        .isEqualTo(MatchKind.EXACT);
    assertThat(mapping.match(Path.fromString("addresses[type eq \"work\"].locality")).kind())
        .isEqualTo(MatchKind.INSIDE);
    assertThat(mapping.match(Path.fromString("addresses[type eq \"work\"]")).kind())
        .isEqualTo(MatchKind.INSIDE);
    assertThat(mapping.match(Path.fromString("nickName")).kind()).isEqualTo(MatchKind.NONE);
  }
}
