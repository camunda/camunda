/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.exceptions.MigrationException;
import java.io.IOException;
import java.util.List;

public interface StepsRepository {

  void updateSteps() throws IOException, MigrationException;

  void save(final Step step) throws MigrationException, IOException;

  List<Step> findAll() throws IOException;

  List<Step> findNotAppliedFor(final String indexName) throws IOException;

  String getName();

  void refreshIndex();

  List<Step> readStepsFromClasspath() throws IOException;
}
