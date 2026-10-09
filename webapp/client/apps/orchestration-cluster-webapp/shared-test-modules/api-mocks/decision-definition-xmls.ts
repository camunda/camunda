/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const DMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" xmlns:di="http://www.omg.org/spec/DMN/20180521/DI/" id="invoiceBusinessDecisions" name="Invoice Business Decisions" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="invoiceClassification" name="invoiceClassification">
    <decisionTable id="decisionTable">
      <input id="clause1" label="Invoice Amount">
        <inputExpression id="inputExpression1" typeRef="double">
          <text>amount</text>
        </inputExpression>
      </input>
      <output id="clause3" label="Classification" name="invoiceClassification" typeRef="string" />
      <rule id="DecisionRule_1">
        <inputEntry id="LiteralExpression_1">
          <text>&lt; 250</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_2">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1">
      <dmndi:DMNShape id="DMNShape_1" dmnElementRef="invoiceClassification">
        <dc:Bounds height="80" width="180" x="160" y="220" />
      </dmndi:DMNShape>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>`;

const DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" xmlns:di="http://www.omg.org/spec/DMN/20180521/DI/" id="invoiceBusinessDecisions" name="Invoice Business Decisions" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="invoiceClassification" name="invoiceClassification">
    <decisionTable id="decisionTable">
      <input id="clause1" label="Invoice Amount">
        <inputExpression id="inputExpression1" typeRef="double">
          <text>amount</text>
        </inputExpression>
      </input>
      <output id="clause3" label="Classification" name="invoiceClassification" typeRef="string" />
      <rule id="DecisionRule_1">
        <inputEntry id="LiteralExpression_1">
          <text>&lt; 250</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_2">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_2">
        <inputEntry id="LiteralExpression_4">
          <text>[250..1000]</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_5">
          <text>"manager approval"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_3">
        <inputEntry id="LiteralExpression_6">
          <text>&gt; 1000</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_7">
          <text>"board approval"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="calc-key-figures" name="Calculate Credit History Key Figures">
    <variable id="InformationItem_1" name="key_figures" />
    <literalExpression id="LiteralExpression_3" expressionLanguage="feel">
      <text>avg_score: mean(credit_history[type = credit_type].score)</text>
    </literalExpression>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1">
      <dmndi:DMNShape id="DMNShape_1" dmnElementRef="invoiceClassification">
        <dc:Bounds height="80" width="180" x="160" y="220" />
      </dmndi:DMNShape>
      <dmndi:DMNShape id="DMNShape_2" dmnElementRef="calc-key-figures">
        <dc:Bounds height="80" width="180" x="460" y="220" />
      </dmndi:DMNShape>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>`;

export {DMN_XML, DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE};
