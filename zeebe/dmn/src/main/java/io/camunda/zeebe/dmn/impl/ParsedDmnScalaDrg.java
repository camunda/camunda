/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.dmn.parser.ParsedDmn;

public final class ParsedDmnScalaDrg implements ParsedDecisionRequirementsGraph {

  private final ParsedDmn parsedDmn;
  private final String decisionRequirementsId;
  private final String decisionRequirementsName;
  private final String decisionRequirementsNamespace;
  private final List<ParsedDecision> decisions;

  private ParsedDmnScalaDrg(
      final ParsedDmn parsedDmn,
      final String decisionRequirementsId,
      final String decisionRequirementsName,
      final String decisionRequirementsNamespace,
      final List<ParsedDecision> decisions) {
    this.parsedDmn = parsedDmn;
    this.decisionRequirementsId = decisionRequirementsId;
    this.decisionRequirementsName = decisionRequirementsName;
    this.decisionRequirementsNamespace = decisionRequirementsNamespace;
    this.decisions = decisions;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public String getId() {
    return decisionRequirementsId;
  }

  @Override
  public String getName() {
    return decisionRequirementsName;
  }

  @Override
  public String getNamespace() {
    return decisionRequirementsNamespace;
  }

  @Override
  public Collection<ParsedDecision> getDecisions() {
    return decisions;
  }

  public ParsedDmn getParsedDmn() {
    return parsedDmn;
  }

  public static ParsedDmnScalaDrg of(final ParsedDmn parsedDmn) {

    final DmnModelInstance modelInstance = parsedDmn.model();
    final Definitions definitions = modelInstance.getDefinitions();
    final String id = definitions.getId();
    final String name = definitions.getName();
    final String namespace = definitions.getNamespace();
    final List<ParsedDecision> parsedDecisions = getParsedDecisions(parsedDmn);

    return new ParsedDmnScalaDrg(parsedDmn, id, name, namespace, parsedDecisions);
  }

  private static List<ParsedDecision> getParsedDecisions(final ParsedDmn parsedDmn) {
    final var decisions = new ArrayList<ParsedDecision>();

    parsedDmn
        .decisions()
        .foreach(
            decision -> {
              final var parsedDecision = new ParsedDmnScalaDecision(decision.id(), decision.name());
              return decisions.add(parsedDecision);
            });

    return decisions;
  }
}
