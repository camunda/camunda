/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.util.List;

public interface StepsRepository {

  public void save(final Step step);

  public List<Step> findAll();

  public List<Step> findNotAppliedFor(final String indexName);

  public String getName();
}
