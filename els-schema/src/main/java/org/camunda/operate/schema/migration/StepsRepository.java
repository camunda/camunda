/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import org.camunda.operate.exceptions.MigrationException;

import java.io.IOException;
import java.util.List;

public interface StepsRepository {

  void save(final Step step) throws MigrationException, IOException;

  List<Step> findAll() throws IOException;

  List<Step> findNotAppliedFor(final String indexName) throws IOException;

  String getName();
}
