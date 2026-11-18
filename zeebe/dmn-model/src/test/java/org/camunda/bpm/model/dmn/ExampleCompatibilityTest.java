/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.model.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.camunda.bpm.model.dmn.instance.BusinessContextElement;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.DrgElement;
import org.camunda.bpm.model.dmn.instance.ElementCollection;
import org.camunda.bpm.model.dmn.instance.Import;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.InputValues;
import org.camunda.bpm.model.dmn.instance.ItemDefinition;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.OutputEntry;
import org.camunda.bpm.model.dmn.instance.OutputValues;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.dmn.instance.Text;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExampleCompatibilityTest extends DmnModelTest {

  public static final String EXAMPLE_DMN = "org/camunda/bpm/model/dmn/Example.dmn";

  private final DmnModelInstance originalModelInstance;

   @Parameterized.Parameters(name="Namespace: {0}")
   public static Collection<Object[]> parameters(){
     return Arrays.asList(new Object[][]{
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("Example.dmn"))},
         // for compatibility reasons we gotta check the old namespace, too
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("ExampleCompatibility.dmn"))},
         // DMN 1.2
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("Example-DMN-1.2.dmn"))},
         // DMN 1.3
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("Example-DMN-1.3.dmn"))},
         // DMN 1.4
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("Example-DMN-1.4.dmn"))},
         // DMN 1.5
         {Dmn.readModelFromStream(ExampleCompatibilityTest.class.getResourceAsStream("Example-DMN-1.5.dmn"))}
     });
   }

  public ExampleCompatibilityTest(DmnModelInstance originalModelInstance) {
    this.originalModelInstance = originalModelInstance;
  }

  @Before
  public void parseModel() {
    modelInstance = originalModelInstance.clone();
  }

  @Test
  public void shouldGetElements() {

    // Definitions
    Definitions definitions = modelInstance.getDefinitions();
    assertThat(definitions).isNotNull();
    assertThat(definitions.getId()).isEqualTo("definitions");
    assertThat(definitions.getName()).isEqualTo("definitions");
    assertThat(definitions.getNamespace()).isEqualTo(TEST_NAMESPACE);
    assertElementIsEqualToId(definitions, "definitions");

    // Imports
    Collection<Import> imports = definitions.getImports();
    assertThat(imports).isEmpty();

    // Item definitions
    Collection<ItemDefinition> itemDefinitions = definitions.getItemDefinitions();
    assertThat(itemDefinitions).isEmpty();

    // DRG elements
    Collection<DrgElement> drgElements = definitions.getDrgElements();
    assertThat(drgElements).isNotEmpty().hasSize(1);

    // Decision
    Decision decision = (Decision) drgElements.iterator().next();
    assertThat(decision).isNotNull();
    assertThat(decision.getId()).isEqualTo("decision");
    assertThat(decision.getName()).isEqualTo("Check Order");
    assertElementIsEqualToId(decision, "decision");

    // Decision table
    DecisionTable decisionTable = (DecisionTable) decision.getExpression();
    assertThat(decisionTable).isNotNull();
    assertThat(decisionTable.getId()).isEqualTo("decisionTable");
    assertElementIsEqualToId(decisionTable, "decisionTable");

    // Input clauses
    List<Input> inputs = new ArrayList<>(decisionTable.getInputs());
    assertThat(inputs).hasSize(2);
    Iterator<Input> inputIterator = inputs.iterator();

    // 1. Input clause
    Input input = inputIterator.next();
    assertThat(input.getId()).isEqualTo("input1");
    assertThat(input.getLabel()).isEqualTo("Customer Status");
    InputExpression inputExpression = input.getInputExpression();
    assertThat(inputExpression.getId()).isEqualTo("inputExpression1");
    assertThat(inputExpression.getTypeRef()).isEqualTo("string");
    assertThat(inputExpression.getText().getTextContent()).isEqualTo("status");
    InputValues inputValues = input.getInputValues();
    assertThat(inputValues.getText().getTextContent()).isEqualTo("\"bronze\",\"silver\",\"gold\"");

    // 2. Input clause
    input = inputIterator.next();
    assertThat(input.getId()).isEqualTo("input2");
    assertThat(input.getLabel()).isEqualTo("Order Sum");
    inputExpression = input.getInputExpression();
    assertThat(inputExpression.getId()).isEqualTo("inputExpression2");
    assertThat(inputExpression.getTypeRef()).isEqualTo("double");

    // Output clause
    List<Output> outputs = new ArrayList<>(decisionTable.getOutputs());
    assertThat(outputs).hasSize(2);

    // 1. Output clause
    Output output = outputs.get(0);
    assertThat(output.getId()).isEqualTo("output1");
    assertThat(output.getLabel()).isEqualTo("Check Result");
    assertThat(output.getName()).isEqualTo("result");
    assertThat(output.getTypeRef()).isEqualTo("string");
    OutputValues outputValues = output.getOutputValues();
    assertThat(outputValues.getText().getTextContent()).isEqualTo("\"ok\",\"notok\"");

    // 2. Output clause
    output = outputs.get(1);
    assertThat(output.getId()).isEqualTo("output2");
    assertThat(output.getLabel()).isEqualTo("Reason");
    assertThat(output.getName()).isEqualTo("reason");
    assertThat(output.getTypeRef()).isEqualTo("string");

    // Rules
    List<Rule> rules = new ArrayList<>(decisionTable.getRules());

    // 1. Rule
    Rule rule = rules.get(0);
    assertThat(rule.getId()).isEqualTo("rule1");
    List<InputEntry> inputEntries = new ArrayList<>(rule.getInputEntries());
    assertThat(inputEntries).hasSize(2);
    assertThat(inputEntries.get(0).getText().getTextContent()).isEqualTo("\"bronze\"");
    assertThat(inputEntries.get(1).getText().getTextContent()).isEmpty();
    List<OutputEntry> outputEntries = new ArrayList<>(rule.getOutputEntries());
    assertThat(outputEntries).hasSize(2);
    assertThat(outputEntries.get(0).getText().getTextContent()).isEqualTo("\"notok\"");
    assertThat(outputEntries.get(1).getText().getTextContent()).isEqualTo("\"work on your status first, as bronze you're not going to get anything\"");

    // 2. Rule
    rule = rules.get(1);
    assertThat(rule.getId()).isEqualTo("rule2");
    inputEntries = new ArrayList<>(rule.getInputEntries());
    assertThat(inputEntries).hasSize(2);
    assertThat(inputEntries.get(0).getText().getTextContent()).isEqualTo("\"silver\"");
    assertThat(inputEntries.get(1).getText().getTextContent()).isEqualTo("< 1000");
    outputEntries = new ArrayList<>(rule.getOutputEntries());
    assertThat(outputEntries).hasSize(2);
    assertThat(outputEntries.get(0).getText().getTextContent()).isEqualTo("\"ok\"");
    assertThat(outputEntries.get(1).getText().getTextContent()).isEqualTo("\"you little fish will get what you want\"");

    // 3. Rule
    rule = rules.get(2);
    assertThat(rule.getId()).isEqualTo("rule3");
    inputEntries = new ArrayList<>(rule.getInputEntries());
    assertThat(inputEntries).hasSize(2);
    assertThat(inputEntries.get(0).getText().getTextContent()).isEqualTo("\"silver\"");
    assertThat(inputEntries.get(1).getText().getTextContent()).isEqualTo(">= 1000");
    outputEntries = new ArrayList<>(rule.getOutputEntries());
    assertThat(outputEntries).hasSize(2);
    assertThat(outputEntries.get(0).getText().getTextContent()).isEqualTo("\"notok\"");
    assertThat(outputEntries.get(1).getText().getTextContent()).isEqualTo("\"you took too much man, you took too much!\"");

    // 4. Rule
    rule = rules.get(3);
    assertThat(rule.getId()).isEqualTo("rule4");
    inputEntries = new ArrayList<>(rule.getInputEntries());
    assertThat(inputEntries).hasSize(2);
    assertThat(inputEntries.get(0).getText().getTextContent()).isEqualTo("\"gold\"");
    assertThat(inputEntries.get(1).getText().getTextContent()).isEmpty();
    outputEntries = new ArrayList<>(rule.getOutputEntries());
    assertThat(outputEntries).hasSize(2);
    assertThat(outputEntries.get(0).getText().getTextContent()).isEqualTo("\"ok\"");
    assertThat(outputEntries.get(1).getText().getTextContent()).isEqualTo("\"you get anything you want\"");

    // Element collections
    Collection<ElementCollection> elementCollections = definitions.getElementCollections();
    assertThat(elementCollections).isEmpty();

    // Business contexts
    Collection<BusinessContextElement> businessContextElements = definitions.getBusinessContextElements();
    assertThat(businessContextElements).isEmpty();
  }

  @Test
  public void shouldWriteElements() throws Exception {
    modelInstance = Dmn.createEmptyModel();

    // Definitions
    Definitions definitions = generateNamedElement(Definitions.class, "definitions");
    definitions.setNamespace(TEST_NAMESPACE);
    modelInstance.setDocumentElement(definitions);

    // Decision
    Decision decision = generateNamedElement(Decision.class, "Check Order");
    definitions.addChildElement(decision);

    // Decision table
    DecisionTable decisionTable = generateElement(DecisionTable.class);
    decision.addChildElement(decisionTable);

    // 1. Input clause
    Input input = generateElement(Input.class, 1);
    input.setLabel("Customer Status");
    InputExpression inputExpression = generateElement(InputExpression.class, 1);
    inputExpression.setTypeRef("string");
    Text text = generateElement(Text.class);
    text.setTextContent("status");
    inputExpression.setText(text);
    input.setInputExpression(inputExpression);
    InputValues inputValues = generateElement(InputValues.class);
    text = generateElement(Text.class);
    text.setTextContent("\"bronze\",\"silver\",\"gold\"");
    inputValues.setText(text);
    input.setInputValues(inputValues);
    decisionTable.getInputs().add(input);

    // 2. Input clause
    input = generateElement(Input.class, 2);
    input.setLabel("Order Sum");
    inputExpression = generateElement(InputExpression.class, 2);
    inputExpression.setTypeRef("double");
    text = generateElement(Text.class);
    text.setTextContent("sum");
    inputExpression.setText(text);
    input.setInputExpression(inputExpression);
    decisionTable.getInputs().add(input);

    // 1. Output clause
    Output output = generateElement(Output.class, 1);
    output.setLabel("Check Result");
    output.setName("result");
    output.setTypeRef("string");
    OutputValues outputValues = generateElement(OutputValues.class);
    text = generateElement(Text.class);
    text.setTextContent("\"ok\",\"notok\"");
    outputValues.setText(text);
    output.setOutputValues(outputValues);
    decisionTable.getOutputs().add(output);

    // 2. Output clause
    output = generateElement(Output.class, 2);
    output.setLabel("Reason");
    output.setName("reason");
    output.setTypeRef("string");
    decisionTable.getOutputs().add(output);

    // 1. Rule
    Rule rule = generateElement(Rule.class, 1);
    InputEntry inputEntry = generateElement(InputEntry.class, 1);
    text = generateElement(Text.class);
    text.setTextContent("\"bronze\"");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    inputEntry = generateElement(InputEntry.class, 2);
    text = generateElement(Text.class);
    text.setTextContent("");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    OutputEntry outputEntry = generateElement(OutputEntry.class, 1);
    text = generateElement(Text.class);
    text.setTextContent("\"notok\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    outputEntry = generateElement(OutputEntry.class, 2);
    text = generateElement(Text.class);
    text.getDomElement().addCDataSection("\"work on your status first, as bronze you're not going to get anything\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    decisionTable.getRules().add(rule);

    // 2. Rule
    rule = generateElement(Rule.class, 2);
    inputEntry = generateElement(InputEntry.class, 3);
    text = generateElement(Text.class);
    text.setTextContent("\"silver\"");
    rule.getInputEntries().add(inputEntry);
    inputEntry.setText(text);
    inputEntry = generateElement(InputEntry.class, 4);
    text = generateElement(Text.class);
    text.getDomElement().addCDataSection("< 1000");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    outputEntry = generateElement(OutputEntry.class, 3);
    text = generateElement(Text.class);
    text.setTextContent("\"ok\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    outputEntry = generateElement(OutputEntry.class, 4);
    text = generateElement(Text.class);
    text.setTextContent("\"you little fish will get what you want\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    decisionTable.getRules().add(rule);

    // 3. Rule
    rule = generateElement(Rule.class, 3);
    inputEntry = generateElement(InputEntry.class, 5);
    text = generateElement(Text.class);
    text.setTextContent("\"silver\"");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    inputEntry = generateElement(InputEntry.class, 6);
    text = generateElement(Text.class);
    text.getDomElement().addCDataSection(">= 1000");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    outputEntry = generateElement(OutputEntry.class, 5);
    text = generateElement(Text.class);
    text.setTextContent("\"notok\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    outputEntry = generateElement(OutputEntry.class, 6);
    text = generateElement(Text.class);
    text.setTextContent("\"you took too much man, you took too much!\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    decisionTable.getRules().add(rule);

    // 4. Rule
    rule = generateElement(Rule.class, 4);
    inputEntry = generateElement(InputEntry.class, 7);
    text = generateElement(Text.class);
    text.setTextContent("\"gold\"");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    inputEntry = generateElement(InputEntry.class, 8);
    text = generateElement(Text.class);
    text.setTextContent("");
    inputEntry.setText(text);
    rule.getInputEntries().add(inputEntry);
    outputEntry = generateElement(OutputEntry.class, 7);
    text = generateElement(Text.class);
    text.setTextContent("\"ok\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    outputEntry = generateElement(OutputEntry.class, 8);
    text = generateElement(Text.class);
    text.setTextContent("\"you get anything you want\"");
    outputEntry.setText(text);
    rule.getOutputEntries().add(outputEntry);
    decisionTable.getRules().add(rule);

    assertModelEqualsFile(EXAMPLE_DMN);
  }

}
