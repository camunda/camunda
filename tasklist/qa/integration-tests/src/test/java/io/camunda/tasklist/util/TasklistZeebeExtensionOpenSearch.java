/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.qa.util.TestUtil;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "camunda.data.secondary-storage.type", havingValue = "opensearch")
public class TasklistZeebeExtensionOpenSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      cleanupIndicesIfNeeded(prefix -> TestUtil.removeAllIndices(osClient, prefix));
    }
  }

  @Override
  protected DatabaseType getDatabaseType() {
    return DatabaseType.OPENSEARCH;
  }

  @Override
  protected void setSecondaryStorageConfig(final Camunda camunda, final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";

    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.opensearch);
    camunda.getData().getSecondaryStorage().getOpensearch().setUrl(dbUrl);
    camunda.getData().getSecondaryStorage().getOpensearch().setIndexPrefix(indexPrefix);
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
