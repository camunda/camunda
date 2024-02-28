/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.conditions.OpensearchCondition;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class ZeebeRichOpenSearchClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeRichOpenSearchClient.class);
  BeanFactory beanFactory;
  OpenSearchClient openSearchClient;
  private final OpenSearchDocumentOperations openSearchDocumentOperations;
  private final OpenSearchIndexOperations openSearchIndexOperations;
  private final OpenSearchTemplateOperations openSearchTemplateOperations;

  public ZeebeRichOpenSearchClient(
      BeanFactory beanFactory,
      @Qualifier("zeebeOpensearchClient") OpenSearchClient openSearchClient) {
    this.beanFactory = beanFactory;
    this.openSearchClient = openSearchClient;
    openSearchDocumentOperations = new OpenSearchDocumentOperations(LOGGER, openSearchClient);
    openSearchIndexOperations = new OpenSearchIndexOperations(LOGGER, openSearchClient);
    openSearchTemplateOperations = new OpenSearchTemplateOperations(LOGGER, openSearchClient);
  }

  public OpenSearchDocumentOperations doc() {
    return openSearchDocumentOperations;
  }

  public OpenSearchIndexOperations index() {
    return openSearchIndexOperations;
  }

  public OpenSearchTemplateOperations template() {
    return openSearchTemplateOperations;
  }
}
