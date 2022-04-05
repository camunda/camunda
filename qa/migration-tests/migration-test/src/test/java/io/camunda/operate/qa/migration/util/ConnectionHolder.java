/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.util;

import java.io.File;
import java.io.IOException;
import org.apache.http.HttpHost;
import io.camunda.operate.qa.util.migration.TestContext;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectionHolder {

  @Autowired
  private ElasticsearchContainerUtil testContainerUtil;

  @Bean
  public RestHighLevelClient getEsClient(TestContext testContext){
    testContainerUtil.startElasticsearch(testContext);
    return new RestHighLevelClient(
        org.elasticsearch.client.RestClient.builder(new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort(), "http")));
  }

  @Bean
  public TestContext getTestContext(){
    final TestContext testContext = new TestContext();
    testContext.setZeebeDataFolder(createTemporaryFolder());
    return testContext;
  }

  @Bean
  public EntityReader getEntityReader(RestHighLevelClient esClient) {
    return new EntityReader(esClient);
  }


  private File createTemporaryFolder() {
    File createdFolder;
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
