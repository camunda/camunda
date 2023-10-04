/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.OpenSearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.tasklist.util.OpenSearchUtil.getRawResponseWithTenantCheck;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import java.io.IOException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class FormStoreOpenSearch implements FormStore {

  @Autowired private FormIndex formIndex;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  public FormEntity getForm(final String id, final String processDefinitionId) {
    try {
      final String formId = String.format("%s_%s", processDefinitionId, id);
      return getRawResponseWithTenantCheck(
          formId, formIndex, ONLY_RUNTIME, tenantAwareClient, FormEntity.class);
    } catch (IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
