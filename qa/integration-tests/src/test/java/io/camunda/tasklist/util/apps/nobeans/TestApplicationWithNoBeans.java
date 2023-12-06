/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.apps.nobeans;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.graphql.spring.boot.test.GraphQLTestAutoConfiguration;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = GraphQLTestAutoConfiguration.class)
public class TestApplicationWithNoBeans {
  @Bean
  @ConditionalOnMissingBean
  RestClientTransport restClientTransport(
      RestClient restClient, ObjectProvider<RestClientOptions> restClientOptions) {
    return new RestClientTransport(
        restClient, new JacksonJsonpMapper(), restClientOptions.getIfAvailable());
  }
}
