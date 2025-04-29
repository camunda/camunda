/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.response.DecisionInstance;

/** A collection of predefined {@link DecisionSelector}s. */
public class DecisionSelectors {

  /**
   * Select the decision instance by its name.
   *
   * @param decisionName the definition name of the decision.
   * @return the selector
   */
  public static DecisionSelector byName(final String decisionName) {
    return new DecisionNameSelector(decisionName);
  }

  /**
   * Select the decision instance by its name.
   *
   * @param decisionName the definition name of the decision.
   * @param processInstanceKey the associated process instance.
   * @return the selector
   */
  public static DecisionSelector byName(final String decisionName, final long processInstanceKey) {
    return new DecisionNameSelector(decisionName, processInstanceKey);
  }

  /**
   * Select the decision instance by its ID.
   *
   * @param decisionId the definition ID of the decision instance.
   * @return the selector
   */
  public static DecisionSelector byId(final String decisionId) {
    return new DecisionIdSelector(decisionId);
  }

  /**
   * Select the decision instance by its ID.
   *
   * @param decisionId the definition ID of the decision instance.
   * @param processInstanceKey the associated process instance
   * @return the selector
   */
  public static DecisionSelector byId(final String decisionId, final long processInstanceKey) {
    return new DecisionIdSelector(decisionId, processInstanceKey);
  }

  private static final class DecisionIdSelector implements DecisionSelector {

    private final String decisionDefinitionId;
    private final Long processInstanceKey;

    private DecisionIdSelector(final String decisionDefinitionId) {
      this(decisionDefinitionId, null);
    }

    private DecisionIdSelector(final String decisionDefinitionId, final Long processInstanceKey) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final DecisionInstance decisionInstance) {
      return decisionDefinitionId.equals(decisionInstance.getDecisionDefinitionId());
    }

    @Override
    public String describe() {
      if (processInstanceKey != null) {
        return String.format(
            "%s (processInstanceKey: %d)", decisionDefinitionId, processInstanceKey);
      } else {
        return decisionDefinitionId;
      }
    }

    @Override
    public void applyFilter(final DecisionInstanceFilter filter) {
      filter.decisionDefinitionId(decisionDefinitionId);
      if (processInstanceKey != null) {
        filter.processInstanceKey(processInstanceKey);
      }
    }
  }

  private static final class DecisionNameSelector implements DecisionSelector {

    private final String decisionDefinitionName;
    private final Long processInstanceKey;

    private DecisionNameSelector(final String decisionDefinitionName) {
      this(decisionDefinitionName, null);
    }

    private DecisionNameSelector(
        final String decisionDefinitionName, final Long processInstanceKey) {

      this.decisionDefinitionName = decisionDefinitionName;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final DecisionInstance userTask) {
      return decisionDefinitionName.equals(userTask.getDecisionDefinitionName());
    }

    @Override
    public String describe() {
      if (processInstanceKey != null) {
        return String.format(
            "%s (processInstanceKey: %d)", decisionDefinitionName, processInstanceKey);
      } else {
        return decisionDefinitionName;
      }
    }

    @Override
    public void applyFilter(final DecisionInstanceFilter filter) {
      filter.decisionDefinitionName(decisionDefinitionName);
      if (processInstanceKey != null) {
        filter.processInstanceKey(processInstanceKey);
      }
    }
  }
}
