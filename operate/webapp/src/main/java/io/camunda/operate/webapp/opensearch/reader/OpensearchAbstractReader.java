/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class OpensearchAbstractReader {

  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired protected ObjectMapper objectMapper;
}
