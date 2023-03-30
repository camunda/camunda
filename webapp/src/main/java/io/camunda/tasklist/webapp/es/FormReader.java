/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
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

  public FormDTO getFormDTO(final String id, String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);

      final GetRequest getRequest = new GetRequest(formIndex.getFullQualifiedName()).id(formId);

      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        final FormEntity formEntity =
            fromSearchHit(response.getSourceAsString(), objectMapper, FormEntity.class);
        return FormDTO.createFrom(formEntity);
      } else {
        throw new NotFoundException("No task form found with id " + id);
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
