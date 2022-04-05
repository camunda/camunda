/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.camunda.tasklist.webapp.es.FormReader;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FormQueryResolver implements GraphQLQueryResolver {

  @Autowired private FormReader formReader;

  public FormDTO form(String id, String processDefinitionId) {
    return formReader.getFormDTO(id, processDefinitionId);
  }
}
