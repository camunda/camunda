/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import java.util.List;

public class ProcessInstancesByIdArchiverJobIT extends AbstractProcessInstanceArchiverJobIT {

  @Override
  protected ArchiverJob createArchiveJob(final List<Integer> partitionIds) {
    return new ProcessInstancesByIdArchiverJob(
        getArchiver(),
        partitionIds,
        getListViewTemplate(),
        getDependantTemplates(),
        getMetrics(),
        getArchiverRepository(),
        buildScheduler());
  }
}
