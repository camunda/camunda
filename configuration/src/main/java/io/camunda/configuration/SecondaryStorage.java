/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class SecondaryStorage {

  /**
   * When enabled, the default exporter camundaexporter is automatically configured using the
   * secondary-storage properties. Manual configuration of camundaexporter is not necessary. If
   * disabled, camundaexporter will not be configured automatically, but can still be enabled
   * through manual configuration if required. Manual configuration of camundaexporter is generally
   * not recommended, and can result in unexpected behavior if not configured correctly.
   */
  private boolean autoconfigureCamundaExporter = true;
  /** Determines the type of the secondary storage database. */
  private SecondaryStorage.SecondaryStorageType type = SecondaryStorageType.elasticsearch;

  /** Stores the Elasticsearch configuration, when type is set to 'elasticsearch'. */
  @NestedConfigurationProperty private Elasticsearch elasticsearch = new Elasticsearch();

  /** Stores the Opensearch configuration, when type is set to 'opensearch'. */
  @NestedConfigurationProperty private Opensearch opensearch = new Opensearch();

  public boolean getAutoconfigureCamundaExporter() {
    return autoconfigureCamundaExporter;
  }

  public void setAutoconfigureCamundaExporter(final boolean autoconfigureCamundaExporter) {
    this.autoconfigureCamundaExporter = autoconfigureCamundaExporter;
  }

  public SecondaryStorageType getType() {
    return type;
  }

  public void setType(final SecondaryStorageType type) {
    this.type = type;
  }

  public Elasticsearch getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(final Elasticsearch elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public Opensearch getOpensearch() {
    return opensearch;
  }

  public void setOpensearch(final Opensearch opensearch) {
    this.opensearch = opensearch;
  }

  public enum SecondaryStorageType {
    elasticsearch,
    opensearch,
    rdbms,
    none;
  }
}
