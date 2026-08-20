/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;

/**
 * Configuration for which extension property names are exported as tool properties.
 *
 * <p>These properties control which BPMN extension properties are extracted and stored as
 * structured attributes on entities, making them queryable without exporting all extension
 * properties. While currently used primarily for message subscriptions, these settings are general
 * and can be applied to other entity types in the future.
 */
public class ExtensionProperties {

  public static final String DEFAULT_TOOL_NAME_PROPERTY =
      ExtensionPropertyConfiguration.DEFAULT_TOOL_NAME_PROPERTY;
  public static final String DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY =
      ExtensionPropertyConfiguration.DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY;
  public static final String DEFAULT_TOOL_PROPERTIES_PREFIX =
      ExtensionPropertyConfiguration.DEFAULT_TOOL_PROPERTIES_PREFIX;

  /** The extension property name whose value is exported as the {@code toolName} attribute. */
  private String toolNameProperty = ExtensionPropertyConfiguration.DEFAULT_TOOL_NAME_PROPERTY;

  /**
   * The extension property name whose value is exported as the {@code inboundConnectorType}
   * attribute.
   */
  private String inboundConnectorTypeProperty =
      ExtensionPropertyConfiguration.DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY;

  /**
   * The extension property name prefix for properties exported as the {@code toolProperties}
   * key-value attribute. Only extension properties whose names start with this prefix are exported.
   */
  private String toolPropertiesPrefix =
      ExtensionPropertyConfiguration.DEFAULT_TOOL_PROPERTIES_PREFIX;

  public String getToolNameProperty() {
    return toolNameProperty;
  }

  public void setToolNameProperty(final String toolNameProperty) {
    this.toolNameProperty = toolNameProperty;
  }

  public String getInboundConnectorTypeProperty() {
    return inboundConnectorTypeProperty;
  }

  public void setInboundConnectorTypeProperty(final String inboundConnectorTypeProperty) {
    this.inboundConnectorTypeProperty = inboundConnectorTypeProperty;
  }

  public String getToolPropertiesPrefix() {
    return toolPropertiesPrefix;
  }

  public void setToolPropertiesPrefix(final String toolPropertiesPrefix) {
    this.toolPropertiesPrefix = toolPropertiesPrefix;
  }
}
