/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.decision;

import io.camunda.optimize.service.util.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.HitPolicy;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.DmnElement;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.OutputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.dmn.instance.Text;

/**
 * A generator class to build DMN tables/models in a programmatic way.
 *
 * <p>Note: do not reuse a generator instance two create different kinds of dmn tables, since the
 * model instance is shared between the generator instances and cloned each time it is passed on.
 */
public class DmnModelGenerator {

  private static final String TEST_NAMESPACE = "http://camunda.org/schema/1.0/dmn";
  private static final String DEFAULT_DRD_KEY = "aDecisionRequirementKey";
  private static final String DEFAULT_DRD_NAME = "aDecisionRequirementName";

  private String decisionRequirementDiagramKey = DEFAULT_DRD_KEY;
  private String decisionRequirementDiagramName = DEFAULT_DRD_NAME;
  private final DmnModelInstance modelInstance = Dmn.createEmptyModel();

  private final List<Decision> decisions = new ArrayList<>();

  public DmnModelGenerator decisionRequirementDiagramKey(
      final String decisionRequirementDiagramKey) {
    this.decisionRequirementDiagramKey = decisionRequirementDiagramKey;
    return this;
  }

  public DmnModelGenerator decisionRequirementDiagramName(
      final String decisionRequirementDiagramName) {
    this.decisionRequirementDiagramName = decisionRequirementDiagramName;
    return this;
  }

  public DecisionGenerator decision() {
    return new DecisionGenerator(this, modelInstance);
  }

  public DmnModelInstance build() {
    final Definitions definitions =
        generateNamedElement(Definitions.class, decisionRequirementDiagramName, modelInstance);
    definitions.setId(decisionRequirementDiagramKey);
    definitions.setNamespace(TEST_NAMESPACE);
    decisions.forEach(definitions::addChildElement);

    modelInstance.setDefinitions(definitions);
    return modelInstance;
  }

  public static DmnModelGenerator create() {
    return new DmnModelGenerator();
  }

  private static <E extends NamedElement> E generateNamedElement(
      final Class<E> elementClass, final String name, final DmnModelInstance modelInstance) {
    final E element = generateElement(elementClass, modelInstance);
    element.setName(name);
    return element;
  }

  private static <E extends DmnElement> E generateElement(
      final Class<E> elementClass, final DmnModelInstance modelInstance) {
    final E element = modelInstance.newInstance(elementClass);
    String identifier = elementClass.getSimpleName();
    identifier = Character.toLowerCase(identifier.charAt(0)) + identifier.substring(1);
    element.setId(identifier);
    return element;
  }

  public class DecisionGenerator {

    private static final String DEFAULT_DECISION_DEFINITION_KEY = "aDecision";
    private static final String DEFAULT_DECISION_NAME = "aDecisionName";

    private final DmnModelGenerator dmnModelGenerator;
    private final DmnModelInstance modelInstance;

    private final List<Input> inputs = new ArrayList<>();
    private final List<Output> outputs = new ArrayList<>();
    private String decisionDefinitionKey;
    private String decisionDefinitionName;
    private String decisionDefinitionVersionTag;
    private final List<Rule> rules = new ArrayList<>();
    private HitPolicy hitPolicy = HitPolicy.UNIQUE;

    public DecisionGenerator(
        final DmnModelGenerator dmnModelGenerator, final DmnModelInstance modelInstance) {
      this.dmnModelGenerator = dmnModelGenerator;
      this.modelInstance = modelInstance;
      decisionDefinitionKey = DEFAULT_DECISION_DEFINITION_KEY + dmnModelGenerator.decisions.size();
      decisionDefinitionName = DEFAULT_DECISION_NAME + dmnModelGenerator.decisions.size();
    }

    public DecisionGenerator decisionDefinitionKey(final String decisionKey) {
      decisionDefinitionKey = decisionKey;
      return this;
    }

    public DecisionGenerator decisionDefinitionName(final String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    public DecisionGenerator decisionDefinitionVersionTag(
        final String decisionDefinitionVersionTag) {
      this.decisionDefinitionVersionTag = decisionDefinitionVersionTag;
      return this;
    }

    public DecisionGenerator addInput(
        final String label,
        final String inputIdClause,
        final String camInputVariable,
        final DecisionTypeRef decisionTypeRef) {
      final Input input = generateElement(Input.class, modelInstance);
      input.setId(inputIdClause);
      input.setLabel(label);
      final InputExpression inputExpression = generateElement(InputExpression.class, modelInstance);
      inputExpression.setTypeRef(decisionTypeRef.getId());
      inputExpression.setId("LiteralExpression_" + IdGenerator.getNextId());
      final Text text = modelInstance.newInstance(Text.class);
      text.setTextContent(camInputVariable);
      inputExpression.setText(text);
      input.setInputExpression(inputExpression);
      inputs.add(input);
      return this;
    }

    public DecisionGenerator addInput(
        final String label, final String camInputVariable, final DecisionTypeRef decisionTypeRef) {
      return addInput(
          label, "InputClause_" + IdGenerator.getNextId(), camInputVariable, decisionTypeRef);
    }

    public DecisionGenerator addInput(final String label, final DecisionTypeRef decisionTypeRef) {
      return addInput(label, "CamInputVariable_" + IdGenerator.getNextId(), decisionTypeRef);
    }

    public DecisionGenerator addOutput(
        final String label,
        final String outputClauseId,
        final String camOutputVariable,
        final DecisionTypeRef decisionTypeRef) {
      final Output output = generateElement(Output.class, modelInstance);
      output.setId(outputClauseId);
      output.setLabel(label);
      output.setTypeRef(decisionTypeRef.getId());
      output.setName(camOutputVariable);
      outputs.add(output);
      return this;
    }

    public DecisionGenerator addOutput(
        final String label, final String camOutputVariable, final DecisionTypeRef decisionTypeRef) {
      return addOutput(
          label, "OutputClause_" + IdGenerator.getNextId(), camOutputVariable, decisionTypeRef);
    }

    public DecisionGenerator addOutput(final String label, final DecisionTypeRef decisionTypeRef) {
      return addOutput(label, "CamOutPutVariable_" + IdGenerator.getNextId(), decisionTypeRef);
    }

    public DecisionGenerator setHitPolicy(final HitPolicy hitPolicy) {
      this.hitPolicy = hitPolicy;
      return this;
    }

    public RuleGenerator rule() {
      return new RuleGenerator(this);
    }

    private DecisionGenerator addRule(final Rule rule) {
      rules.add(rule);
      return this;
    }

    public DmnModelGenerator buildDecision() {
      final Decision decision =
          generateNamedElement(Decision.class, decisionDefinitionName, modelInstance);
      decision.setId(decisionDefinitionKey);
      decision.setVersionTag(decisionDefinitionVersionTag);

      final DecisionTable decisionTable = generateElement(DecisionTable.class, modelInstance);
      decisionTable.setHitPolicy(hitPolicy);
      decisionTable.setId("DecisionTable_" + IdGenerator.getNextId());
      decision.setExpression(decisionTable);

      decisionTable.getInputs().addAll(inputs);
      decisionTable.getOutputs().addAll(outputs);
      decisionTable.getRules().addAll(rules);

      dmnModelGenerator.decisions.add(decision);
      return dmnModelGenerator;
    }
  }

  public class RuleGenerator {

    private final DecisionGenerator decisionGenerator;
    private final DmnModelInstance modelInstance;
    private final List<InputEntry> inputEntries = new ArrayList<>();
    private final List<OutputEntry> outputEntries = new ArrayList<>();

    public RuleGenerator(final DecisionGenerator decisionGenerator) {
      modelInstance = decisionGenerator.modelInstance;
      this.decisionGenerator = decisionGenerator;
    }

    public RuleGenerator addStringInputEntry(final String expression) {
      final Text text = modelInstance.newInstance(Text.class);
      text.setTextContent(expression);

      final InputEntry inputEntry = modelInstance.newInstance(InputEntry.class);
      inputEntry.setText(text);
      inputEntries.add(inputEntry);
      return this;
    }

    public RuleGenerator addStringOutputEntry(final String expression) {
      final Text text = modelInstance.newInstance(Text.class);
      text.setTextContent(expression);

      final OutputEntry outputEntry = modelInstance.newInstance(OutputEntry.class);
      outputEntry.setText(text);
      outputEntries.add(outputEntry);
      return this;
    }

    public DecisionGenerator buildRule() {
      return buildRule(null);
    }

    public DecisionGenerator buildRule(final String ruleId) {
      final Rule rule = modelInstance.newInstance(Rule.class, ruleId);
      if (inputEntries.size() != decisionGenerator.inputs.size()) {
        throw new RuntimeException("The number of inputs and input entries must match!");
      }
      if (outputEntries.size() != decisionGenerator.outputs.size()) {
        throw new RuntimeException("The number of outputs and output entries must match!");
      }
      rule.getInputEntries().addAll(inputEntries);
      rule.getOutputEntries().addAll(outputEntries);
      decisionGenerator.rules.add(rule);
      return decisionGenerator;
    }
  }
}
