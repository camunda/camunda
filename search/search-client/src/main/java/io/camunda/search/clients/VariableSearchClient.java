/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;

public interface VariableSearchClient extends AutoCloseable {

  Either<Exception, SearchQueryResult<VariableEntity>> searchVariables(
      VariableQuery filter, Authentication authentication);
}
