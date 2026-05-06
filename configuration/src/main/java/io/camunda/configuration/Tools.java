/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Configuration for which extension property names are exported as tool properties.
 *
 * <p>These properties control which BPMN extension properties are extracted and stored as
 * structured attributes on entities, making them queryable without exporting all extension
 * properties. While currently used primarily for message subscriptions, these settings are general
 * and can be applied to other entity types in the future.
 */
public class Tools {

  public static final String DEFAULT_EXTENSION_PROPERTY_TOOL_NAME = "io.camunda.tool:name";
  public static final String DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE = "inbound.type";
  public static final String DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES = "io.camunda.tool:";

  /** The extension property name whose value is exported as the {@code toolName} attribute. */
  private String extensionPropertyToolName = DEFAULT_EXTENSION_PROPERTY_TOOL_NAME;

  /**
   * The extension property name whose value is exported as the {@code inboundConnectorType}
   * attribute.
   */
  private String extensionPropertyInboundConnectorType =
      DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE;

  /**
   * The extension property name prefix for properties exported as the {@code toolProperties}
   * key-value attribute. Only extension properties whose names start with this prefix are exported.
   */
  private String extensionPropertyPrefixToolProperties =
      DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES;

  public String getExtensionPropertyToolName() {
    return extensionPropertyToolName;
  }

  public void setExtensionPropertyToolName(final String extensionPropertyToolName) {
    this.extensionPropertyToolName = extensionPropertyToolName;
  }

  public String getExtensionPropertyInboundConnectorType() {
    return extensionPropertyInboundConnectorType;
  }

  public void setExtensionPropertyInboundConnectorType(
      final String extensionPropertyInboundConnectorType) {
    this.extensionPropertyInboundConnectorType = extensionPropertyInboundConnectorType;
  }

  public String getExtensionPropertyPrefixToolProperties() {
    return extensionPropertyPrefixToolProperties;
  }

  public void setExtensionPropertyPrefixToolProperties(
      final String extensionPropertyPrefixToolProperties) {
    this.extensionPropertyPrefixToolProperties = extensionPropertyPrefixToolProperties;
  }
}
