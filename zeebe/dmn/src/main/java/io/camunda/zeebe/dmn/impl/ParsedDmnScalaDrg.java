/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionInput;
import io.camunda.zeebe.dmn.ParsedDecisionOutput;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.dmn.ParsedDecisionRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.ExtensionElements;
import org.camunda.dmn.parser.ParsedDecisionTable;
import org.camunda.dmn.parser.ParsedDmn;

public final class ParsedDmnScalaDrg implements ParsedDecisionRequirementsGraph {

  // duplicated from the BPMN model's ZeebeConstants, which this module does not depend on
  private static final String ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0";
  private static final String ELEMENT_VERSION_TAG = "versionTag";
  private static final String ATTRIBUTE_VALUE = "value";

  private final ParsedDmn parsedDmn;
  private final String decisionRequirementsId;
  private final String decisionRequirementsName;
  private final String decisionRequirementsNamespace;
  private final List<ParsedDecision> decisions;
  private final Map<String, String> versionTagsByDecisionId;

  private ParsedDmnScalaDrg(
      final ParsedDmn parsedDmn,
      final String decisionRequirementsId,
      final String decisionRequirementsName,
      final String decisionRequirementsNamespace,
      final List<ParsedDecision> decisions,
      final Map<String, String> versionTagsByDecisionId) {
    this.parsedDmn = parsedDmn;
    this.decisionRequirementsId = decisionRequirementsId;
    this.decisionRequirementsName = decisionRequirementsName;
    this.decisionRequirementsNamespace = decisionRequirementsNamespace;
    this.decisions = decisions;
    this.versionTagsByDecisionId = versionTagsByDecisionId;
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

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  /**
   * @return the parsed DMN for evaluation; its {@link ParsedDmn#model()} is always {@code null}
   */
  public ParsedDmn getParsedDmn() {
    return parsedDmn;
  }

  public Optional<String> getVersionTag(final String decisionId) {
    return Optional.ofNullable(versionTagsByDecisionId.get(decisionId));
  }

  public static ParsedDmnScalaDrg of(final ParsedDmn parsedDmn) {

    final DmnModelInstance modelInstance = parsedDmn.model();
    final Definitions definitions = modelInstance.getDefinitions();
    final String id = definitions.getId();
    final String name = definitions.getName();
    final String namespace = definitions.getNamespace();
    final List<ParsedDecision> parsedDecisions = getParsedDecisions(parsedDmn);
    final Map<String, String> versionTags = getVersionTags(modelInstance);

    // The model instance is a DOM of the whole resource (~10x its size) and is not needed for
    // evaluation. Dropping it lets it be collected while the DRG stays cached.
    final ParsedDmn parsedDmnWithoutModel = new ParsedDmn(null, parsedDmn.decisions());

    return new ParsedDmnScalaDrg(
        parsedDmnWithoutModel, id, name, namespace, parsedDecisions, versionTags);
  }

  private static Map<String, String> getVersionTags(final DmnModelInstance modelInstance) {
    final var versionTags = new HashMap<String, String>();
    for (final Decision decision : modelInstance.getModelElementsByType(Decision.class)) {
      if (decision.getId() == null) {
        // let the deployment validation reject the decision with a clear message
        continue;
      }
      Optional.ofNullable(decision.getUniqueChildElementByType(ExtensionElements.class))
          .map(
              extensionElements ->
                  extensionElements.getUniqueChildElementByNameNs(ZEEBE_NS, ELEMENT_VERSION_TAG))
          .map(versionTag -> versionTag.getAttributeValue(ATTRIBUTE_VALUE))
          .ifPresent(versionTag -> versionTags.put(decision.getId(), versionTag));
    }
    return Map.copyOf(versionTags);
  }

  private static List<ParsedDecision> getParsedDecisions(final ParsedDmn parsedDmn) {
    final var decisions = new ArrayList<ParsedDecision>();

    parsedDmn
        .decisions()
        .foreach(
            decision -> {
              final var parsedDecision =
                  new ParsedDmnScalaDecision(
                      decision.id(), decision.name(), getParsedDecisionTable(decision));
              return decisions.add(parsedDecision);
            });

    return decisions;
  }

  private static ParsedDmnScalaDecisionTable getParsedDecisionTable(
      final org.camunda.dmn.parser.ParsedDecision parsedDecision) {
    if (!(parsedDecision.logic() instanceof final ParsedDecisionTable table)) {
      return null;
    }

    final List<ParsedDecisionInput> inputs = new ArrayList<>();
    final List<ParsedDecisionOutput> outputs = new ArrayList<>();
    final List<ParsedDecisionRule> rules = new ArrayList<>();

    table.inputs().foreach(i -> inputs.add(new ParsedDmnScalaDecisionInput(i.id(), i.name())));
    table.outputs().foreach(i -> outputs.add(new ParsedDmnScalaDecisionOutput(i.id(), i.name())));
    table.rules().foreach(i -> rules.add(new ParsedDmnScalaDecisionRule(i.id())));

    return new ParsedDmnScalaDecisionTable(inputs, outputs, rules);
  }
}
