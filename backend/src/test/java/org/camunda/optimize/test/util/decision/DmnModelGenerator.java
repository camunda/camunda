/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.decision;


import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
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
import org.camunda.optimize.service.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * A generator class to build DMN tables/models in a programmatic way.
 *
 * Note: do not reuse a generator instance two create different kinds
 * of dmn tables, since the model instance is shared between the generator instances
 * and cloned each time it is passed on.
 */
public class DmnModelGenerator {

  public static final String DEFAULT_DECISION_DEFINITION_KEY = "aDecision";
  private static final String DEFAULT_DECISION_NAME = "aDecisionName";
  private static final String TEST_NAMESPACE = "http://camunda.org/schema/1.0/dmn";
  private final DmnModelInstance modelInstance = Dmn.createEmptyModel();
  private List<Input> inputs = new ArrayList<>();
  private List<Output> outputs = new ArrayList<>();
  private String decisionDefinitionKey = DEFAULT_DECISION_DEFINITION_KEY;
  private String decisionDefinitionName = DEFAULT_DECISION_NAME;
  private List<Rule> rules = new ArrayList<>();

  public DmnModelGenerator decisionDefinitionKey(String decisionKey) {
    this.decisionDefinitionKey = decisionKey;
    return this;
  }

  public DmnModelGenerator decisionDefinitionName(String decisionDefinitionName) {
    this.decisionDefinitionName = decisionDefinitionName;
    return this;
  }

  public DmnModelGenerator addInput(String label, String camInputVariable, DecisionTypeRef decisionTypeRef) {
    Input input = generateElement(Input.class, modelInstance);
    input.setId("InputClause_" + IdGenerator.getNextId());
    input.setLabel(label);
    InputExpression inputExpression = generateElement(InputExpression.class, modelInstance);
    inputExpression.setTypeRef(decisionTypeRef.getId());
    inputExpression.setId("LiteralExpression_" + IdGenerator.getNextId());
    Text text = modelInstance.newInstance(Text.class);
    text.setTextContent(camInputVariable);
    inputExpression.setText(text);
    input.setInputExpression(inputExpression);
    this.inputs.add(input);
    return this;
  }

  public DmnModelGenerator addInput(String label, DecisionTypeRef decisionTypeRef) {
    return addInput(label, "someInputVar", decisionTypeRef);
  }

  public DmnModelGenerator addOutput(String label, String camOutputVariable, DecisionTypeRef decisionTypeRef) {
    Output output = generateElement(Output.class, modelInstance);
    output.setId("OutputClause_" + IdGenerator.getNextId());
    output.setLabel(label);
    output.setTypeRef(decisionTypeRef.getId());
    output.setName(camOutputVariable);
    this.outputs.add(output);
    return this;
  }

  public DmnModelGenerator addOutput(String label, DecisionTypeRef decisionTypeRef) {
    return addOutput(label, RandomStringUtils.random(10), decisionTypeRef);
  }

  public DmnModelInstance build() {
    Decision decision = generateNamedElement(Decision.class, decisionDefinitionName, modelInstance);
    decision.setId(decisionDefinitionKey);

    DecisionTable decisionTable = generateElement(DecisionTable.class, modelInstance);
    decisionTable.setId("decisionTable");
    decision.setExpression(decisionTable);

    decisionTable.getInputs().addAll(inputs);
    decisionTable.getOutputs().addAll(outputs);
    decisionTable.getRules().addAll(this.rules);

    final Definitions definitions = generateNamedElement(Definitions.class, "definitions", modelInstance);
    definitions.setNamespace(TEST_NAMESPACE);
    definitions.addChildElement(decision);

    modelInstance.setDefinitions(definitions);

    return modelInstance;
  }

  public RuleGenerator rule() {
    return new RuleGenerator(this);
  }

  public static DmnModelGenerator create() {
    return new DmnModelGenerator();
  }

  private DmnModelGenerator addRule(Rule rule) {
    this.rules.add(rule);
    return this;
  }

  private static <E extends NamedElement> E generateNamedElement(final Class<E> elementClass,
                                                                 final String name,
                                                                 final DmnModelInstance modelInstance) {
    E element = generateElement(elementClass, modelInstance);
    element.setName(name);
    return element;
  }

  private static <E extends DmnElement> E generateElement(final Class<E> elementClass,
                                                          final DmnModelInstance modelInstance) {
    E element = modelInstance.newInstance(elementClass);
    String identifier = elementClass.getSimpleName();
    identifier = Character.toLowerCase(identifier.charAt(0)) + identifier.substring(1);
    element.setId(identifier);
    return element;
  }

  public class RuleGenerator {

    private DmnModelGenerator dmnModelGenerator;
    private DmnModelInstance modelInstance;
    private List<InputEntry> inputEntries = new ArrayList<>();
    private List<OutputEntry> outputEntries = new ArrayList<>();

    public RuleGenerator(DmnModelGenerator dmnModelGenerator) {
      this.modelInstance = dmnModelGenerator.modelInstance;
      this.dmnModelGenerator = dmnModelGenerator;
    }

    public DmnModelGenerator buildRule() {
      Rule rule = modelInstance.newInstance(Rule.class);
      if (inputEntries.size() != dmnModelGenerator.inputs.size()) {
        throw new RuntimeException("The number of inputs and input entries must match!");
      }
      if (outputEntries.size() != dmnModelGenerator.outputs.size()) {
        throw new RuntimeException("The number of outputs and output entries must match!");
      }
      rule.getInputEntries().addAll(inputEntries);
      rule.getOutputEntries().addAll(outputEntries);
      this.dmnModelGenerator.rules.add(rule);
      return this.dmnModelGenerator;
    }

    public RuleGenerator addStringInputEntry(String expression) {
      Text text = modelInstance.newInstance(Text.class);
      text.setTextContent("\"" + expression + "\"");

      InputEntry inputEntry = modelInstance.newInstance(InputEntry.class);
      inputEntry.setText(text);
      this.inputEntries.add(inputEntry);
      return this;
    }

    public RuleGenerator addStringOutputEntry(String expression) {
      Text text = modelInstance.newInstance(Text.class);
      text.setTextContent("\"" + expression + "\"");

      OutputEntry outputEntry = modelInstance.newInstance(OutputEntry.class);
      outputEntry.setText(text);
      this.outputEntries.add(outputEntry);
      return this;
    }

  }
}
