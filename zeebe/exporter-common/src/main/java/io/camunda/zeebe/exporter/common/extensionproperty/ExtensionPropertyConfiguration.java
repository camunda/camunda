/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.extensionproperty;

import java.util.function.Predicate;

/** Exporter-side configuration for which BPMN extension properties map to tool attributes. */
public class ExtensionPropertyConfiguration {

  public static final String DEFAULT_TOOL_NAME_PROPERTY = "io.camunda.tool:name";
  public static final String DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY = "inbound.type";
  public static final String DEFAULT_TOOL_PROPERTIES_PREFIX = "io.camunda.tool:";

  private String toolNameProperty = DEFAULT_TOOL_NAME_PROPERTY;
  private String inboundConnectorTypeProperty = DEFAULT_INBOUND_CONNECTOR_TYPE_PROPERTY;
  private String toolPropertiesPrefix = DEFAULT_TOOL_PROPERTIES_PREFIX;

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

  public Predicate<String> extensionPropertyFilter() {
    return name ->
        name.equals(toolNameProperty)
            || name.equals(inboundConnectorTypeProperty)
            || (toolPropertiesPrefix != null
                && !toolPropertiesPrefix.isBlank()
                && name.startsWith(toolPropertiesPrefix));
  }
}
