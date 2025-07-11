/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;

public interface DecisionRequirementsReader
    extends SearchEntityReader<DecisionRequirementsEntity, DecisionRequirementsQuery> {

  DecisionRequirementsEntity getByKey(
      final String key, final boolean includeXml, final ResourceAccessChecks resourceAccessChecks);
}
