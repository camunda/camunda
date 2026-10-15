/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.controller;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SCIM discovery endpoints, served from JSON documents on the classpath. The configured attributes
 * that live in an extension schema are added to {@code /Schemas}, typed as best effort.
 */
@RestController
@RequestMapping("/scim/v2")
public class ScimDiscoveryController {

  private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(ScimJson.MEDIA_TYPE);

  private final String serviceProviderConfig = load("ServiceProviderConfig");
  private final String resourceTypes = load("ResourceTypes");
  private final String schemas;

  public ScimDiscoveryController() {
    this(
        ScimAttributeMapping.empty(ScimUserMapper.USER_SCHEMA),
        ScimAttributeMapping.empty(ScimGroupMapper.GROUP_SCHEMA));
  }

  public ScimDiscoveryController(
      final ScimAttributeMapping userAttributes, final ScimAttributeMapping groupAttributes) {
    schemas = withExtensionSchemas(load("Schemas"), userAttributes, groupAttributes);
  }

  @GetMapping("/ServiceProviderConfig")
  public ResponseEntity<String> getServiceProviderConfig() {
    return ok(serviceProviderConfig);
  }

  @GetMapping("/ResourceTypes")
  public ResponseEntity<String> getResourceTypes() {
    return ok(resourceTypes);
  }

  @GetMapping("/Schemas")
  public ResponseEntity<String> getSchemas() {
    return ok(schemas);
  }

  private static String withExtensionSchemas(
      final String document, final ScimAttributeMapping... mappings) {
    final Map<String, ObjectNode> extensions = new LinkedHashMap<>();
    for (final var mapping : mappings) {
      for (final var attribute : mapping.attributes()) {
        if (attribute.urn() == null) {
          continue;
        }
        final var schema =
            extensions.computeIfAbsent(
                attribute.urn().toLowerCase(Locale.ROOT), key -> newSchema(attribute.urn()));
        final var name = attribute.names().getFirst();
        final var attributes = (ArrayNode) schema.get("attributes");
        boolean known = false;
        for (final var existing : attributes) {
          known |= name.equals(existing.get("name").asString());
        }
        if (!known) {
          final var node = attributes.addObject();
          node.put("name", name);
          node.put("type", attribute.names().size() > 1 ? "complex" : "string");
          node.put("multiValued", false);
          node.put("required", false);
          node.put("mutability", "readWrite");
          node.put("returned", "default");
          node.put("uniqueness", "none");
        }
      }
    }
    if (extensions.isEmpty()) {
      return document;
    }
    try {
      final var root = ScimJson.read(document, ObjectNode.class);
      final var resources = (ArrayNode) root.get("Resources");
      extensions.values().forEach(resources::add);
      root.put("totalResults", resources.size());
      root.put("itemsPerPage", resources.size());
      return ScimJson.write(root);
    } catch (final BadRequestException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ObjectNode newSchema(final String urn) {
    final var schema = ScimJson.newObject();
    schema.putArray("schemas").add("urn:ietf:params:scim:schemas:core:2.0:Schema");
    schema.put("id", urn);
    schema.put("name", urn.substring(urn.lastIndexOf(':') + 1) + " extension");
    schema.put("description", "Configured attributes, stored on the Camunda resource");
    schema.putArray("attributes");
    return schema;
  }

  private static ResponseEntity<String> ok(final String body) {
    return ResponseEntity.ok().contentType(SCIM_MEDIA_TYPE).body(body);
  }

  private static String load(final String name) {
    try {
      return new ClassPathResource("scim/" + name + ".json")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
