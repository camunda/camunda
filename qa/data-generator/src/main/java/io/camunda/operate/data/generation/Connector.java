/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.generation;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;

@Configuration
public class Connector {

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired
  private DataGeneratorProperties dataGeneratorProperties;

  public ZeebeClient createZeebeClient() {
    String gatewayAddress = dataGeneratorProperties.getZeebeGatewayAddress();
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
      .gatewayAddress(gatewayAddress)
      .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE)
      .usePlaintext();
    //TODO test the connection?
    return builder.build();
  }

  @Bean
  public ZeebeClient getZeebeClient() {
    return createZeebeClient();
  }

  @Bean
  public RestHighLevelClient createRestHighLevelClient(){
    return new RestHighLevelClient(
      RestClient.builder(new HttpHost(dataGeneratorProperties.getElasticsearchHost(), dataGeneratorProperties.getElasticsearchPort(), "http")));
  }


}
