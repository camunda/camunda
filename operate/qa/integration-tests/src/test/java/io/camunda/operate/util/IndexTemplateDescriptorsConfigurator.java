/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class IndexTemplateDescriptorsConfigurator {
  @Bean
  public ProcessIndex getProcessIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new ProcessIndex(
        operateProperties.getIndexPrefix(DatabaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public ListViewTemplate getListViewTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new ListViewTemplate(
        operateProperties.getIndexPrefix(DatabaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }
}
