/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es;

import static io.zeebe.tasklist.util.ElasticsearchUtil.fromSearchHit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.FormEntity;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.schema.indices.FormIndex;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.graphql.entity.FormDTO;
import java.io.IOException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FormReader {

  @Autowired private FormIndex formIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  public FormDTO getFormDTO(final String id) {
    try {
      final GetRequest getRequest =
          new GetRequest(formIndex.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, id);

      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        final FormEntity formEntity =
            fromSearchHit(response.getSourceAsString(), objectMapper, FormEntity.class);
        return FormDTO.createFrom(formEntity);
      } else {
        throw new IllegalArgumentException("No task form found with id " + id);
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
