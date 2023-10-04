/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.getRawResponseWithTenantCheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class FormStoreElasticSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ObjectMapper objectMapper;

  public FormEntity getForm(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      final var formSearchHit =
          getRawResponseWithTenantCheck(formId, formIndex, ONLY_RUNTIME, tenantAwareClient);
      return fromSearchHit(formSearchHit.getSourceAsString(), objectMapper, FormEntity.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
