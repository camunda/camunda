/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Configuration for which extension property names are exported for message subscriptions.
 *
 * <p>These properties control which BPMN extension properties are extracted and stored as
 * structured attributes on message subscription entities, making them queryable without exporting
 * all extension properties.
 */
public class MessageSubscriptionConfig {

  public static final String DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_TOOL_NAME =
      "io.camunda.tool:name";
  public static final String DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_INBOUND_CONNECTOR_TYPE =
      "inbound.type";
  public static final String DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_PREFIX_TOOL_PROPERTIES =
      "io.camunda.tool:";

  /** The extension property name whose value is exported as the {@code toolName} attribute. */
  private String extensionPropertyAttributeToolName =
      DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_TOOL_NAME;

  /**
   * The extension property name whose value is exported as the {@code inboundConnectorType}
   * attribute.
   */
  private String extensionPropertyAttributeInboundConnectorType =
      DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_INBOUND_CONNECTOR_TYPE;

  /**
   * The extension property name prefix for properties exported as the {@code toolProperties}
   * key-value attribute. Only extension properties whose names start with this prefix are exported.
   */
  private String extensionPropertyAttributePrefixToolProperties =
      DEFAULT_EXTENSION_PROPERTY_ATTRIBUTE_PREFIX_TOOL_PROPERTIES;

  public String getExtensionPropertyAttributeToolName() {
    return extensionPropertyAttributeToolName;
  }

  public void setExtensionPropertyAttributeToolName(
      final String extensionPropertyAttributeToolName) {
    this.extensionPropertyAttributeToolName = extensionPropertyAttributeToolName;
  }

  public String getExtensionPropertyAttributeInboundConnectorType() {
    return extensionPropertyAttributeInboundConnectorType;
  }

  public void setExtensionPropertyAttributeInboundConnectorType(
      final String extensionPropertyAttributeInboundConnectorType) {
    this.extensionPropertyAttributeInboundConnectorType =
        extensionPropertyAttributeInboundConnectorType;
  }

  public String getExtensionPropertyAttributePrefixToolProperties() {
    return extensionPropertyAttributePrefixToolProperties;
  }

  public void setExtensionPropertyAttributePrefixToolProperties(
      final String extensionPropertyAttributePrefixToolProperties) {
    this.extensionPropertyAttributePrefixToolProperties =
        extensionPropertyAttributePrefixToolProperties;
  }
}
