/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Route} from '@playwright/test';
import {DecisionInstanceDto} from 'modules/api/decisionInstances/fetchDecisionInstance';
import {DrdDataDto} from 'modules/api/decisionInstances/fetchDrdData';

const mockEvaluatedDecisionInstance: DecisionInstanceDto = {
  id: '2251799813830820-1',
  tenantId: '<default>',
  state: 'EVALUATED',
  decisionType: 'DECISION_TABLE',
  decisionDefinitionId: '2251799813687886',
  decisionId: 'invoiceClassification',
  decisionName: 'Invoice Classification',
  decisionVersion: 2,
  evaluationDate: '2023-08-14T05:47:07.123+0000',
  errorMessage: null,
  processInstanceId: '2251799813830813',
  result: '"budget"',
  evaluatedInputs: [
    {
      id: 'clause1',
      name: 'Invoice Amount',
      value: '1000',
    },
    {
      id: 'InputClause_15qmk0v',
      name: 'Invoice Category',
      value: '"Misc"',
    },
  ],
  evaluatedOutputs: [
    {
      id: 'clause3',
      name: 'Classification',
      value: '"budget"',
      ruleId: 'DecisionRule_1ak4z14',
      ruleIndex: 2,
    },
  ],
};

const mockEvaluatedDrdData: DrdDataDto = {
  invoiceAssignApprover: [
    {
      decisionInstanceId: '2251799813830820-3',
      state: 'EVALUATED',
    },
  ],
  invoiceClassification: [
    {
      decisionInstanceId: '2251799813830820-1',
      state: 'EVALUATED',
    },
  ],
  amountToString: [
    {
      decisionInstanceId: '2251799813830820-2',
      state: 'EVALUATED',
    },
  ],
};

const mockEvaluatedXml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" xmlns:di="http://www.omg.org/spec/DMN/20180521/DI/" xmlns:camunda="http://camunda.org/schema/1.0/dmn" id="invoiceBusinessDecisions" name="Invoice Business Decisions" namespace="http://camunda.org/schema/1.0/dmn" exporter="Camunda Modeler" exporterVersion="4.12.0">
  <decision id="invoiceClassification" name="Invoice Classification">
    <decisionTable id="decisionTable">
      <input id="clause1" label="Invoice Amount" camunda:inputVariable="">
        <inputExpression id="inputExpression1" typeRef="double">
          <text>amount</text>
        </inputExpression>
      </input>
      <input id="InputClause_15qmk0v" label="Invoice Category" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1oi86cw" typeRef="string">
          <text>invoiceCategory</text>
        </inputExpression>
        <inputValues id="UnaryTests_0kisa67">
          <text>"Travel Expenses","Misc","Software License Costs"</text>
        </inputValues>
      </input>
      <output id="clause3" label="Classification" name="invoiceClassification" typeRef="string">
        <outputValues id="UnaryTests_08dl8wf">
          <text>"day-to-day expense","budget","exceptional"</text>
        </outputValues>
      </output>
      <rule id="DecisionRule_1of5a87">
        <inputEntry id="LiteralExpression_0yrqmtg">
          <text>&lt; 250</text>
        </inputEntry>
        <inputEntry id="UnaryTests_06edsin">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_046antl">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_1ak4z14">
        <inputEntry id="LiteralExpression_0qmsef6">
          <text>[250..1000]</text>
        </inputEntry>
        <inputEntry id="UnaryTests_09b743h">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_05xxvip">
          <text>"budget"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-4">
        <inputEntry id="UnaryTests_0le0gl8">
          <text>&gt; 1000</text>
        </inputEntry>
        <inputEntry id="UnaryTests_0pukamj">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1e76ugx">
          <text>"exceptional"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_0cuxolz">
        <inputEntry id="LiteralExpression_05lyjk7">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_0ve4z34">
          <text>"Travel Expenses"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1bq8m03">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-2">
        <inputEntry id="UnaryTests_1nssdlk">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_01ppb4l">
          <text>"Software License Costs"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0y00iih">
          <text>"budget"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="invoiceAssignApprover" name="Assign Approver Group">
    <informationRequirement id="InformationRequirement_1kkeocv">
      <requiredDecision href="#invoiceClassification" />
    </informationRequirement>
    <informationRequirement id="InformationRequirement_0uzhmkt">
      <requiredDecision href="#amountToString" />
    </informationRequirement>
    <decisionTable id="DecisionTable_16o85h8" hitPolicy="COLLECT">
      <input id="InputClause_0og2hn3" label="Invoice Classification" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1vywt5q" typeRef="string">
          <text>invoiceClassification</text>
        </inputExpression>
        <inputValues id="UnaryTests_0by7qiy">
          <text>"day-to-day expense","budget","exceptional"</text>
        </inputValues>
      </input>
      <output id="OutputClause_1cthd0w" label="Approver Group" name="result" typeRef="string">
        <outputValues id="UnaryTests_1ulmk9p">
          <text>"management","accounting","sales"</text>
        </outputValues>
      </output>
      <rule id="row-49839158-1">
        <inputEntry id="UnaryTests_18ifczd">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0sgxulk">
          <text>"accounting"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-6">
        <inputEntry id="UnaryTests_0kfae8g">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1iksrro">
          <text>"sales"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-5">
        <inputEntry id="UnaryTests_08cevwi">
          <text>"budget", "exceptional"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0c7hz8g">
          <text>"management"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="amountToString" name="Convert amount to string">
    <variable id="InformationItem_1qw1dn9" name="amountStr" typeRef="string" />
    <literalExpression id="LiteralExpression_0gfbl7s">
      <text>"$" + string(amount)</text>
    </literalExpression>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1cuuevk">
      <dmndi:DMNShape id="DMNShape_1abvt5s" dmnElementRef="invoiceClassification">
        <dc:Bounds height="55" width="100" x="153" y="215" />
      </dmndi:DMNShape>
      <dmndi:DMNShape id="DMNShape_1ay7af5" dmnElementRef="invoiceAssignApprover">
        <dc:Bounds height="55" width="100" x="224" y="84" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1wn1950" dmnElementRef="InformationRequirement_1kkeocv">
        <di:waypoint x="203" y="215" />
        <di:waypoint x="257" y="159" />
        <di:waypoint x="257" y="139" />
      </dmndi:DMNEdge>
      <dmndi:DMNShape id="DMNShape_0kgcvw6" dmnElementRef="amountToString">
        <dc:Bounds height="80" width="180" x="320" y="203" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1sucp5a" dmnElementRef="InformationRequirement_0uzhmkt">
        <di:waypoint x="410" y="203" />
        <di:waypoint x="291" y="159" />
        <di:waypoint x="291" y="139" />
      </dmndi:DMNEdge>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>
`;

const mockEvaluatedDecisionInstanceWithoutPanels: DecisionInstanceDto = {
  id: '2251799813830820-2',
  tenantId: '<default>',
  state: 'EVALUATED',
  decisionType: 'LITERAL_EXPRESSION',
  decisionDefinitionId: '2251799813687887',
  decisionId: 'amountToString',
  decisionName: 'Convert amount to string',
  decisionVersion: 1,
  evaluationDate: '2023-08-14T05:47:07.123+0000',
  errorMessage: null,
  processInstanceId: '2251799813830813',
  result: '"$1000"',
  evaluatedInputs: [],
  evaluatedOutputs: [],
};

const mockEvaluatedDrdDataWithoutPanels: DrdDataDto = {
  invoiceAssignApprover: [
    {
      decisionInstanceId: '2251799813830820-3',
      state: 'EVALUATED',
    },
  ],
  invoiceClassification: [
    {
      decisionInstanceId: '2251799813830820-1',
      state: 'EVALUATED',
    },
  ],
  amountToString: [
    {
      decisionInstanceId: '2251799813830820-2',
      state: 'EVALUATED',
    },
  ],
};

const mockEvaluatedXmlWithoutPanels = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" xmlns:di="http://www.omg.org/spec/DMN/20180521/DI/" xmlns:camunda="http://camunda.org/schema/1.0/dmn" id="invoiceBusinessDecisions" name="Invoice Business Decisions" namespace="http://camunda.org/schema/1.0/dmn" exporter="Camunda Modeler" exporterVersion="4.12.0">
  <decision id="invoiceClassification" name="Invoice Classification">
    <decisionTable id="decisionTable">
      <input id="clause1" label="Invoice Amount" camunda:inputVariable="">
        <inputExpression id="inputExpression1" typeRef="double">
          <text>amount</text>
        </inputExpression>
      </input>
      <input id="InputClause_15qmk0v" label="Invoice Category" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1oi86cw" typeRef="string">
          <text>invoiceCategory</text>
        </inputExpression>
        <inputValues id="UnaryTests_0kisa67">
          <text>"Travel Expenses","Misc","Software License Costs"</text>
        </inputValues>
      </input>
      <output id="clause3" label="Classification" name="invoiceClassification" typeRef="string">
        <outputValues id="UnaryTests_08dl8wf">
          <text>"day-to-day expense","budget","exceptional"</text>
        </outputValues>
      </output>
      <rule id="DecisionRule_1of5a87">
        <inputEntry id="LiteralExpression_0yrqmtg">
          <text>&lt; 250</text>
        </inputEntry>
        <inputEntry id="UnaryTests_06edsin">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_046antl">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_1ak4z14">
        <inputEntry id="LiteralExpression_0qmsef6">
          <text>[250..1000]</text>
        </inputEntry>
        <inputEntry id="UnaryTests_09b743h">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_05xxvip">
          <text>"budget"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-4">
        <inputEntry id="UnaryTests_0le0gl8">
          <text>&gt; 1000</text>
        </inputEntry>
        <inputEntry id="UnaryTests_0pukamj">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1e76ugx">
          <text>"exceptional"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_0cuxolz">
        <inputEntry id="LiteralExpression_05lyjk7">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_0ve4z34">
          <text>"Travel Expenses"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1bq8m03">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-2">
        <inputEntry id="UnaryTests_1nssdlk">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_01ppb4l">
          <text>"Software License Costs"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0y00iih">
          <text>"budget"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="invoiceAssignApprover" name="Assign Approver Group">
    <informationRequirement id="InformationRequirement_1kkeocv">
      <requiredDecision href="#invoiceClassification" />
    </informationRequirement>
    <informationRequirement id="InformationRequirement_0uzhmkt">
      <requiredDecision href="#amountToString" />
    </informationRequirement>
    <decisionTable id="DecisionTable_16o85h8" hitPolicy="COLLECT">
      <input id="InputClause_0og2hn3" label="Invoice Classification" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1vywt5q" typeRef="string">
          <text>invoiceClassification</text>
        </inputExpression>
        <inputValues id="UnaryTests_0by7qiy">
          <text>"day-to-day expense","budget","exceptional"</text>
        </inputValues>
      </input>
      <output id="OutputClause_1cthd0w" label="Approver Group" name="result" typeRef="string">
        <outputValues id="UnaryTests_1ulmk9p">
          <text>"management","accounting","sales"</text>
        </outputValues>
      </output>
      <rule id="row-49839158-1">
        <inputEntry id="UnaryTests_18ifczd">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0sgxulk">
          <text>"accounting"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-6">
        <inputEntry id="UnaryTests_0kfae8g">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1iksrro">
          <text>"sales"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-5">
        <inputEntry id="UnaryTests_08cevwi">
          <text>"budget", "exceptional"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0c7hz8g">
          <text>"management"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="amountToString" name="Convert amount to string">
    <variable id="InformationItem_1qw1dn9" name="amountStr" typeRef="string" />
    <literalExpression id="LiteralExpression_0gfbl7s">
      <text>"$" + string(amount)</text>
    </literalExpression>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1cuuevk">
      <dmndi:DMNShape id="DMNShape_1abvt5s" dmnElementRef="invoiceClassification">
        <dc:Bounds height="55" width="100" x="153" y="215" />
      </dmndi:DMNShape>
      <dmndi:DMNShape id="DMNShape_1ay7af5" dmnElementRef="invoiceAssignApprover">
        <dc:Bounds height="55" width="100" x="224" y="84" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1wn1950" dmnElementRef="InformationRequirement_1kkeocv">
        <di:waypoint x="203" y="215" />
        <di:waypoint x="257" y="159" />
        <di:waypoint x="257" y="139" />
      </dmndi:DMNEdge>
      <dmndi:DMNShape id="DMNShape_0kgcvw6" dmnElementRef="amountToString">
        <dc:Bounds height="80" width="180" x="320" y="203" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1sucp5a" dmnElementRef="InformationRequirement_0uzhmkt">
        <di:waypoint x="410" y="203" />
        <di:waypoint x="291" y="159" />
        <di:waypoint x="291" y="139" />
      </dmndi:DMNEdge>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>
`;

const mockFailedDecisionInstance: DecisionInstanceDto = {
  id: '6755399441062312-1',
  tenantId: '<default>',
  state: 'FAILED',
  decisionType: 'DECISION_TABLE',
  decisionDefinitionId: '2251799813687886',
  decisionId: 'invoiceClassification',
  decisionName: 'Invoice Classification',
  decisionVersion: 2,
  evaluationDate: '2023-08-14T05:47:06.793+0000',
  errorMessage:
    "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
  processInstanceId: '6755399441062307',
  result: 'null',
  evaluatedInputs: [],
  evaluatedOutputs: [],
};

const mockFailedDrdData: DrdDataDto = {
  invoiceClassification: [
    {
      decisionInstanceId: '6755399441062312-1',
      state: 'FAILED',
    },
  ],
};

const mockFailedXml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" xmlns:di="http://www.omg.org/spec/DMN/20180521/DI/" xmlns:camunda="http://camunda.org/schema/1.0/dmn" id="invoiceBusinessDecisions" name="Invoice Business Decisions" namespace="http://camunda.org/schema/1.0/dmn" exporter="Camunda Modeler" exporterVersion="4.12.0">
  <decision id="invoiceClassification" name="Invoice Classification">
    <decisionTable id="decisionTable">
      <input id="clause1" label="Invoice Amount" camunda:inputVariable="">
        <inputExpression id="inputExpression1" typeRef="double">
          <text>amount</text>
        </inputExpression>
      </input>
      <input id="InputClause_15qmk0v" label="Invoice Category" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1oi86cw" typeRef="string">
          <text>invoiceCategory</text>
        </inputExpression>
        <inputValues id="UnaryTests_0kisa67">
          <text>"Travel Expenses","Misc","Software License Costs"</text>
        </inputValues>
      </input>
      <output id="clause3" label="Classification" name="invoiceClassification" typeRef="string">
        <outputValues id="UnaryTests_08dl8wf">
          <text>"day-to-day expense","budget","exceptional"</text>
        </outputValues>
      </output>
      <rule id="DecisionRule_1of5a87">
        <inputEntry id="LiteralExpression_0yrqmtg">
          <text>&lt; 250</text>
        </inputEntry>
        <inputEntry id="UnaryTests_06edsin">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_046antl">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_1ak4z14">
        <inputEntry id="LiteralExpression_0qmsef6">
          <text>[250..1000]</text>
        </inputEntry>
        <inputEntry id="UnaryTests_09b743h">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_05xxvip">
          <text>"budget"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-4">
        <inputEntry id="UnaryTests_0le0gl8">
          <text>&gt; 1000</text>
        </inputEntry>
        <inputEntry id="UnaryTests_0pukamj">
          <text>"Misc"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1e76ugx">
          <text>"exceptional"</text>
        </outputEntry>
      </rule>
      <rule id="DecisionRule_0cuxolz">
        <inputEntry id="LiteralExpression_05lyjk7">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_0ve4z34">
          <text>"Travel Expenses"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1bq8m03">
          <text>"day-to-day expense"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-2">
        <inputEntry id="UnaryTests_1nssdlk">
          <text></text>
        </inputEntry>
        <inputEntry id="UnaryTests_01ppb4l">
          <text>"Software License Costs"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0y00iih">
          <text>"budget"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="invoiceAssignApprover" name="Assign Approver Group">
    <informationRequirement id="InformationRequirement_1kkeocv">
      <requiredDecision href="#invoiceClassification" />
    </informationRequirement>
    <informationRequirement id="InformationRequirement_0uzhmkt">
      <requiredDecision href="#amountToString" />
    </informationRequirement>
    <decisionTable id="DecisionTable_16o85h8" hitPolicy="COLLECT">
      <input id="InputClause_0og2hn3" label="Invoice Classification" camunda:inputVariable="">
        <inputExpression id="LiteralExpression_1vywt5q" typeRef="string">
          <text>invoiceClassification</text>
        </inputExpression>
        <inputValues id="UnaryTests_0by7qiy">
          <text>"day-to-day expense","budget","exceptional"</text>
        </inputValues>
      </input>
      <output id="OutputClause_1cthd0w" label="Approver Group" name="result" typeRef="string">
        <outputValues id="UnaryTests_1ulmk9p">
          <text>"management","accounting","sales"</text>
        </outputValues>
      </output>
      <rule id="row-49839158-1">
        <inputEntry id="UnaryTests_18ifczd">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0sgxulk">
          <text>"accounting"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-6">
        <inputEntry id="UnaryTests_0kfae8g">
          <text>"day-to-day expense"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_1iksrro">
          <text>"sales"</text>
        </outputEntry>
      </rule>
      <rule id="row-49839158-5">
        <inputEntry id="UnaryTests_08cevwi">
          <text>"budget", "exceptional"</text>
        </inputEntry>
        <outputEntry id="LiteralExpression_0c7hz8g">
          <text>"management"</text>
        </outputEntry>
      </rule>
    </decisionTable>
  </decision>
  <decision id="amountToString" name="Convert amount to string">
    <variable id="InformationItem_1qw1dn9" name="amountStr" typeRef="string" />
    <literalExpression id="LiteralExpression_0gfbl7s">
      <text>"$" + string(amount)</text>
    </literalExpression>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1cuuevk">
      <dmndi:DMNShape id="DMNShape_1abvt5s" dmnElementRef="invoiceClassification">
        <dc:Bounds height="55" width="100" x="153" y="215" />
      </dmndi:DMNShape>
      <dmndi:DMNShape id="DMNShape_1ay7af5" dmnElementRef="invoiceAssignApprover">
        <dc:Bounds height="55" width="100" x="224" y="84" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1wn1950" dmnElementRef="InformationRequirement_1kkeocv">
        <di:waypoint x="203" y="215" />
        <di:waypoint x="257" y="159" />
        <di:waypoint x="257" y="139" />
      </dmndi:DMNEdge>
      <dmndi:DMNShape id="DMNShape_0kgcvw6" dmnElementRef="amountToString">
        <dc:Bounds height="80" width="180" x="320" y="203" />
      </dmndi:DMNShape>
      <dmndi:DMNEdge id="DMNEdge_1sucp5a" dmnElementRef="InformationRequirement_0uzhmkt">
        <di:waypoint x="410" y="203" />
        <di:waypoint x="291" y="159" />
        <di:waypoint x="291" y="139" />
      </dmndi:DMNEdge>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>
`;

function mockResponses({
  decisionInstanceDetail,
  drdData,
  xml,
}: {
  decisionInstanceDetail?: DecisionInstanceDto;
  drdData?: DrdDataDto;
  xml?: string;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('drd-data')) {
      return route.fulfill({
        status: drdData === undefined ? 400 : 200,
        body: JSON.stringify(drdData),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/decision-instances/')) {
      return route.fulfill({
        status: decisionInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(decisionInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: JSON.stringify(xml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

export {
  mockEvaluatedDecisionInstance,
  mockEvaluatedDrdData,
  mockEvaluatedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDrdDataWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
  mockFailedDecisionInstance,
  mockFailedDrdData,
  mockFailedXml,
  mockResponses,
};
