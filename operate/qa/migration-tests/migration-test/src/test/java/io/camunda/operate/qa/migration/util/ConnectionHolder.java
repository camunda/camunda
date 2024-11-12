/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.util;

import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.TestContext;
import java.io.File;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectionHolder {

  @Autowired private TestContainerUtil testContainerUtil;

  @Bean
  public RestHighLevelClient getEsClient(TestContext testContext) {
    testContainerUtil.startElasticsearch(testContext);
    return new RestHighLevelClient(
        org.elasticsearch.client.RestClient.builder(
            new HttpHost(
                testContext.getExternalElsHost(), testContext.getExternalElsPort(), "http")));
  }

  @Bean
  public TestContext getTestContext() {
    final TestContext testContext = new TestContext();
    testContext.setZeebeDataFolder(createTemporaryFolder());
    testContext.setZeebeIndexPrefix("migration-test");
    // Disable CSRF for migration tests, because CSRF implementation is different between Operate
    // version
    testContext.addOperateContainerEnv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false");
    return testContext;
  }

  @Bean
  public EntityReader getEntityReader(RestHighLevelClient esClient) {
    return new EntityReader(esClient);
  }

  private File createTemporaryFolder() {
    final File createdFolder;
    try {
      createdFolder = File.createTempFile("junit", "", null);
      createdFolder.delete();
      createdFolder.mkdir();
      return createdFolder;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
