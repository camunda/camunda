/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.store.FormStore;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class FormStoreOpenSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private OpenSearchClient openSearchClient;

  public FormEntity getForm(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);

      final var formEntityResponse =
          openSearchClient.get(
              b -> b.index(formIndex.getFullQualifiedName()).id(formId), FormEntity.class);

      if (formEntityResponse.found()) {
        return formEntityResponse.source();
      } else {
        throw new NotFoundException("No task form found with id " + id);
      }
    } catch (IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
