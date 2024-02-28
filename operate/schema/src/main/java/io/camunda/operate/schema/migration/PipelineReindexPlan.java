/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.schema.SchemaManager;
import java.util.List;
import java.util.Optional;

public abstract class PipelineReindexPlan implements ReindexPlan {

  protected List<Step> steps = List.of();
  protected String srcIndex;
  protected String dstIndex;

  @Override
  public ReindexPlan setSrcIndex(String srcIndex) {
    this.srcIndex = srcIndex;
    return this;
  }

  @Override
  public ReindexPlan setDstIndex(String dstIndex) {
    this.dstIndex = dstIndex;
    return this;
  }

  @Override
  public ReindexPlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  protected Optional<String> createPipelineFromSteps(final SchemaManager schemaManager)
      throws MigrationException {
    if (steps.isEmpty()) {
      return Optional.empty();
    }
    final String name = srcIndex + "-to-" + dstIndex + "-pipeline";
    boolean added = schemaManager.addPipeline(name, getPipelineDefinition());
    if (added) {
      return Optional.of(name);
    } else {
      throw new MigrationException(String.format("Couldn't create '%s' pipeline.", name));
    }
  }

  protected abstract String getPipelineDefinition();
}
