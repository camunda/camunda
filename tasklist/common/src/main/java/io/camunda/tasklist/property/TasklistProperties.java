/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This class contains all project configuration parameters.
 *
 * <p>NOTE: Some of the fields of this object are overridden with values coming from the Unified
 * Configuration system, from the object
 * io.camunda.configuration.beanoverrides.TasklistPropertiesOverride
 */
public class TasklistProperties {

  public static final String PREFIX = "camunda.tasklist";
  public static final String ALPHA_RELEASES_SUFIX = "alpha";
  public static final String ELASTIC_SEARCH = "elasticsearch";
  public static final String OPEN_SEARCH = "opensearch";
  private static final String UNKNOWN_VERSION = "unknown-version";

  private String database = ELASTIC_SEARCH;

  private boolean enterprise = false;

  @Value("${camunda.tasklist.internal.version.current}")
  private String version = UNKNOWN_VERSION;

  @NestedConfigurationProperty
  private TasklistElasticsearchProperties elasticsearch = new TasklistElasticsearchProperties();

  @NestedConfigurationProperty
  private TasklistOpenSearchProperties openSearch = new TasklistOpenSearchProperties();

  @NestedConfigurationProperty private ZeebeProperties zeebe = new ZeebeProperties();

  @NestedConfigurationProperty private ImportProperties importer = new ImportProperties();

  @NestedConfigurationProperty private ClientProperties client = new ClientProperties();

  @NestedConfigurationProperty private CloudProperties cloud = new CloudProperties();

  @NestedConfigurationProperty
  private FeatureFlagProperties featureFlag = new FeatureFlagProperties();

  @NestedConfigurationProperty private Auth0Properties auth0 = new Auth0Properties();

  @NestedConfigurationProperty private IdentityProperties identity = new IdentityProperties();

  @NestedConfigurationProperty private BackupProperties backup = new BackupProperties();

  @NestedConfigurationProperty
  private TasklistDocumentationProperties documentation = new TasklistDocumentationProperties();

  public boolean isSelfManaged() {
    return getCloud().getClusterId() == null;
  }

  public TasklistElasticsearchProperties getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(final TasklistElasticsearchProperties elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public ZeebeProperties getZeebe() {
    return zeebe;
  }

  public void setZeebe(final ZeebeProperties zeebe) {
    this.zeebe = zeebe;
  }

  public ImportProperties getImporter() {
    return importer;
  }

  public void setImporter(final ImportProperties importer) {
    this.importer = importer;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  public void setEnterprise(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public ClientProperties getClient() {
    return client;
  }

  public void setClient(final ClientProperties client) {
    this.client = client;
  }

  public Auth0Properties getAuth0() {
    return auth0;
  }

  public TasklistProperties setAuth0(final Auth0Properties auth0) {
    this.auth0 = auth0;
    return this;
  }

  public IdentityProperties getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityProperties identity) {
    this.identity = identity;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public void setCloud(final CloudProperties cloud) {
    this.cloud = cloud;
  }

  public BackupProperties getBackup() {
    return backup;
  }

  public TasklistProperties setBackup(final BackupProperties backup) {
    this.backup = backup;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public TasklistProperties setVersion(final String version) {
    this.version = version;
    return this;
  }

  public FeatureFlagProperties getFeatureFlag() {
    return featureFlag;
  }

  public TasklistProperties setFeatureFlag(final FeatureFlagProperties featureFlag) {
    this.featureFlag = featureFlag;
    return this;
  }

  public TasklistOpenSearchProperties getOpenSearch() {
    return openSearch;
  }

  public TasklistProperties setOpenSearch(final TasklistOpenSearchProperties openSearch) {
    this.openSearch = openSearch;
    return this;
  }

  public String getDatabase() {
    return database;
  }

  public TasklistProperties setDatabase(final String database) {
    this.database = database;
    return this;
  }

  public TasklistDocumentationProperties getDocumentation() {
    return documentation;
  }

  public void setDocumentation(final TasklistDocumentationProperties documentation) {
    this.documentation = documentation;
  }

  public boolean isElasticsearchDB() {
    return ELASTIC_SEARCH.equals(database);
  }

  public String getIndexPrefix() {
    return switch (database) {
      case ELASTIC_SEARCH -> getElasticsearch().getIndexPrefix();
      case OPEN_SEARCH -> getOpenSearch().getIndexPrefix();
      default -> null;
    };
  }
}
