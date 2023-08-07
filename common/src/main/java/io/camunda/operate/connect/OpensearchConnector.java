/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.connect;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

@Profile("opensearch")
@Configuration
public class OpensearchConnector {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchConnector.class);

  @Bean
  public OpenSearchClient openSearchClient(){
    final HttpHost host = new HttpHost("http", "localhost", 9205);
    final OpenSearchTransport transport = ApacheHttpClient5TransportBuilder.builder(host).build();
    final OpenSearchClient openSearchClient = new OpenSearchClient(transport);
    try {
      HealthResponse response = openSearchClient.cluster().health();
      logger.info("OpenSearch cluster health: {}", response.status());
    } catch (IOException e) {
      logger.error("Error in getting health status from {}", "localhost:9205", e);
    }
    return openSearchClient;
  }
}
