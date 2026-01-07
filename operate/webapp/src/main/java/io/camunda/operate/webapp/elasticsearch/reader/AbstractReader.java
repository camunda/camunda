/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractReader {

  @Autowired protected RestHighLevelClient esClient;

  @Qualifier("es8Client")
  @Autowired
  protected ElasticsearchClient es8client;

  @Autowired protected TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired protected ElasticsearchTenantHelper tenantHelper;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;
}
