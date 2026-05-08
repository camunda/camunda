/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tools;

import java.util.function.Predicate;

/** Exporter-side configuration for which BPMN extension properties map to tool attributes. */
public class ToolsConfiguration {

  public static final String DEFAULT_EXTENSION_PROPERTY_TOOL_NAME = "io.camunda.tool:name";
  public static final String DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE = "inbound.type";
  public static final String DEFAULT_EXTENSION_PROPERTY_PREFIX_TOOL_PROPERTIES = "io.camunda.tool:";

  private String extensionPropertyToolName = DEFAULT_EXTENSION_PROPERTY_TOOL_NAME;
  private String extensionPropertyInboundConnectorType =
      DEFAULT_EXTENSION_PROPERTY_INBOUND_CONNECTOR_TYPE;
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

  public Predicate<String> extensionPropertyFilter() {
    return name ->
        name.equals(extensionPropertyToolName)
            || name.equals(extensionPropertyInboundConnectorType)
            || (extensionPropertyPrefixToolProperties != null
                && !extensionPropertyPrefixToolProperties.isBlank()
                && name.startsWith(extensionPropertyPrefixToolProperties));
  }
}
