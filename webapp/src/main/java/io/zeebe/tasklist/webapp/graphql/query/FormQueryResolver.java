/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import io.zeebe.tasklist.webapp.es.FormReader;
import io.zeebe.tasklist.webapp.graphql.entity.FormDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FormQueryResolver implements GraphQLQueryResolver {

  @Autowired private FormReader formReader;

  public FormDTO form(String id, DataFetchingEnvironment dataFetchingEnvironment) {
    return formReader.getFormDTO(id);
  }
}
