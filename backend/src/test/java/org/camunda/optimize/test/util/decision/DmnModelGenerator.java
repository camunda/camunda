/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;


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
import org.camunda.optimize.service.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * A generator class to build DMN tables/models in a programmatic way.
 * <p>
 * Note: do not reuse a generator instance two create different kinds
 * of dmn tables, since the model instance is shared between the generator instances
 * and cloned each time it is passed on.
 */
public class DmnModelGenerator {

  private static final String TEST_NAMESPACE = "http://camunda.org/schema/1.0/dmn";
  private static final String DEFAULT_DRD_KEY = "aDecisionRequirementKey";
  private static final String DEFAULT_DRD_NAME = "aDecisionRequirementName";

  private String decisionRequirementDiagramKey = DEFAULT_DRD_KEY;
  private String decisionRequirementDiagramName = DEFAULT_DRD_NAME;
  private final DmnModelInstance modelInstance = Dmn.createEmptyModel();

  private List<Decision> decisions = new ArrayList<>();

  public DmnModelGenerator decisionRequirementDiagramKey(String decisionRequirementDiagramKey) {
    this.decisionRequirementDiagramKey = decisionRequirementDiagramKey;
    return this;
  }

  public DmnModelGenerator decisionRequirementDiagramName(String decisionRequirementDiagramName) {
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

  public class DecisionGenerator {

    private static final String DEFAULT_DECISION_DEFINITION_KEY = "aDecision";
    private static final String DEFAULT_DECISION_NAME = "aDecisionName";

    private DmnModelGenerator dmnModelGenerator;
    private DmnModelInstance modelInstance;

    private List<Input> inputs = new ArrayList<>();
    private List<Output> outputs = new ArrayList<>();
    private String decisionDefinitionKey;
    private String decisionDefinitionName;
    private String decisionDefinitionVersionTag;
    private List<Rule> rules = new ArrayList<>();
    private HitPolicy hitPolicy = HitPolicy.UNIQUE;

    public DecisionGenerator(final DmnModelGenerator dmnModelGenerator, final DmnModelInstance modelInstance) {
      this.dmnModelGenerator = dmnModelGenerator;
      this.modelInstance = modelInstance;
      decisionDefinitionKey = DEFAULT_DECISION_DEFINITION_KEY + dmnModelGenerator.decisions.size();
      decisionDefinitionName = DEFAULT_DECISION_NAME + dmnModelGenerator.decisions.size();
    }

    public DecisionGenerator decisionDefinitionKey(String decisionKey) {
      this.decisionDefinitionKey = decisionKey;
      return this;
    }

    public DecisionGenerator decisionDefinitionName(String decisionDefinitionName) {
      this.decisionDefinitionName = decisionDefinitionName;
      return this;
    }

    public DecisionGenerator decisionDefinitionVersionTag(String decisionDefinitionVersionTag) {
      this.decisionDefinitionVersionTag = decisionDefinitionVersionTag;
      return this;
    }

    public DecisionGenerator addInput(String label, String inputIdClause, String camInputVariable,
                                      DecisionTypeRef decisionTypeRef) {
      Input input = generateElement(Input.class, modelInstance);
      input.setId(inputIdClause);
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

    public DecisionGenerator addInput(String label, String camInputVariable, DecisionTypeRef decisionTypeRef) {
      return addInput(label, "InputClause_" + IdGenerator.getNextId(), camInputVariable, decisionTypeRef);
    }

    public DecisionGenerator addInput(String label, DecisionTypeRef decisionTypeRef) {
      return addInput(label, "CamInputVariable_" + IdGenerator.getNextId(), decisionTypeRef);
    }

    public DecisionGenerator addOutput(String label, String outputClauseId, String camOutputVariable,
                                       DecisionTypeRef decisionTypeRef) {
      Output output = generateElement(Output.class, modelInstance);
      output.setId(outputClauseId);
      output.setLabel(label);
      output.setTypeRef(decisionTypeRef.getId());
      output.setName(camOutputVariable);
      this.outputs.add(output);
      return this;
    }

    public DecisionGenerator addOutput(String label, String camOutputVariable, DecisionTypeRef decisionTypeRef) {
      return addOutput(label, "OutputClause_" + IdGenerator.getNextId(), camOutputVariable, decisionTypeRef);
    }

    public DecisionGenerator addOutput(String label, DecisionTypeRef decisionTypeRef) {
      return addOutput(label, "CamOutPutVariable_" + IdGenerator.getNextId(), decisionTypeRef);
    }

    public DecisionGenerator setHitPolicy(HitPolicy hitPolicy) {
      this.hitPolicy = hitPolicy;
      return this;
    }

    public RuleGenerator rule() {
      return new RuleGenerator(this);
    }

    private DecisionGenerator addRule(Rule rule) {
      this.rules.add(rule);
      return this;
    }

    public DmnModelGenerator buildDecision() {
      Decision decision = generateNamedElement(Decision.class, decisionDefinitionName, modelInstance);
      decision.setId(decisionDefinitionKey);
      decision.setVersionTag(decisionDefinitionVersionTag);

      DecisionTable decisionTable = generateElement(DecisionTable.class, modelInstance);
      decisionTable.setHitPolicy(hitPolicy);
      decisionTable.setId("DecisionTable_" + IdGenerator.getNextId());
      decision.setExpression(decisionTable);

      decisionTable.getInputs().addAll(inputs);
      decisionTable.getOutputs().addAll(outputs);
      decisionTable.getRules().addAll(this.rules);

      dmnModelGenerator.decisions.add(decision);
      return dmnModelGenerator;
    }

  }

  public class RuleGenerator {

    private DecisionGenerator decisionGenerator;
    private DmnModelInstance modelInstance;
    private List<InputEntry> inputEntries = new ArrayList<>();
    private List<OutputEntry> outputEntries = new ArrayList<>();

    public RuleGenerator(DecisionGenerator decisionGenerator) {
      this.modelInstance = decisionGenerator.modelInstance;
      this.decisionGenerator = decisionGenerator;
    }

    public RuleGenerator addStringInputEntry(String expression) {
      Text text = modelInstance.newInstance(Text.class);
      text.setTextContent(expression);

      InputEntry inputEntry = modelInstance.newInstance(InputEntry.class);
      inputEntry.setText(text);
      this.inputEntries.add(inputEntry);
      return this;
    }

    public RuleGenerator addStringOutputEntry(String expression) {
      Text text = modelInstance.newInstance(Text.class);
      text.setTextContent(expression);

      OutputEntry outputEntry = modelInstance.newInstance(OutputEntry.class);
      outputEntry.setText(text);
      this.outputEntries.add(outputEntry);
      return this;
    }

    public DecisionGenerator buildRule() {
      return buildRule(null);
    }

    public DecisionGenerator buildRule(String ruleId) {
      Rule rule = modelInstance.newInstance(Rule.class, ruleId);
      if (inputEntries.size() != decisionGenerator.inputs.size()) {
        throw new RuntimeException("The number of inputs and input entries must match!");
      }
      if (outputEntries.size() != decisionGenerator.outputs.size()) {
        throw new RuntimeException("The number of outputs and output entries must match!");
      }
      rule.getInputEntries().addAll(inputEntries);
      rule.getOutputEntries().addAll(outputEntries);
      this.decisionGenerator.rules.add(rule);
      return this.decisionGenerator;
    }

  }
}
