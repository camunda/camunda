/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.migration;

import io.zeebe.tasklist.exceptions.MigrationException;
import java.io.IOException;
import java.util.List;

public interface StepsRepository {

  void save(final Step step) throws MigrationException, IOException;

  List<Step> findAll() throws IOException;

  List<Step> findNotAppliedFor(final String indexName) throws IOException;
}
