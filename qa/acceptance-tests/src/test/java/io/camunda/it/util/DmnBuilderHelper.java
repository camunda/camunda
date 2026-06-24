/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.util;

import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.Text;

/** Helper class to build simple DMN models for testing purposes. */
public class DmnBuilderHelper {

  public static DmnModelInstance getDmnModelInstance(final String decisionId) {
    // Create an empty DMN model
    final DmnModelInstance modelInstance = Dmn.createEmptyModel();

    // Create and configure the definitions element
    final Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setName("DRD");
    definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
    modelInstance.setDefinitions(definitions);

    // Create the decision element
    final Decision decision = modelInstance.newInstance(Decision.class);
    decision.setId(decisionId);
    decision.setName("Decision 1");
    definitions.addChildElement(decision);

    // Create the decision table
    final DecisionTable decisionTable = modelInstance.newInstance(DecisionTable.class);
    decision.addChildElement(decisionTable);

    // Add input clauses
    final Input inputExperience = modelInstance.newInstance(Input.class);
    final InputExpression inputExpressionExperience =
        modelInstance.newInstance(InputExpression.class);
    final Text textExperience = modelInstance.newInstance(Text.class);
    textExperience.setTextContent("experience");
    inputExpressionExperience.setText(textExperience);
    inputExperience.setInputExpression(inputExpressionExperience);
    decisionTable.addChildElement(inputExperience);

    // Add output clauses
    final Output outputCode = modelInstance.newInstance(Output.class);
    outputCode.setName("code");
    decisionTable.addChildElement(outputCode);

    return modelInstance;
  }
}
