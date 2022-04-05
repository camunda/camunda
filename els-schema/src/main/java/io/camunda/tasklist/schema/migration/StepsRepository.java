/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.migration;

import io.camunda.tasklist.exceptions.MigrationException;
import java.io.IOException;
import java.util.List;

public interface StepsRepository {

  void updateSteps() throws MigrationException, IOException;

  void save(final Step step) throws MigrationException, IOException;

  List<Step> findAll() throws IOException;

  List<Step> findNotAppliedFor(final String indexName) throws IOException;
}
