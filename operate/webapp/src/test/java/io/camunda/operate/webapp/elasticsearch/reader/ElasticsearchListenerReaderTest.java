/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.SortingDto;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchListenerReaderTest {

  @InjectMocks private ElasticsearchListenerReader underTest;
  @Mock private JobTemplate jobTemplate;
  @Mock private RestHighLevelClient esClient;

  @Test
  public void testGetListenerExecutionsFailWithException() throws IOException {
    when(esClient.search(any(SearchRequest.class), any(RequestOptions.class)))
        .thenThrow(new IOException("Exception to test exception handling."));
    when(jobTemplate.getAlias()).thenReturn("operate-job-8.6.0");

    final Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () ->
                underTest.getListenerExecutions(
                    "1",
                    new ListenerRequestDto()
                        .setFlowNodeId("1")
                        .setPageSize(10)
                        .setSorting(new SortingDto().setSortBy(JobTemplate.TIME))));
    assertTrue(exception.getMessage().contains("while searching for listeners"));
  }
}
