/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect, vi} from 'vitest';
import {DecisionViewer} from './index';

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

describe('<DecisionViewer />', () => {
	it('should render a decision table for a decision table view id', async () => {
		const screen = await render(<DecisionViewer xml={DMN_XML} decisionViewId="invoiceClassification" />);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
	});

	it('should render a literal expression for a literal expression view id', async () => {
		const screen = await render(<DecisionViewer xml={DMN_XML} decisionViewId="calc-key-figures" />);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText(/avg_score/)).toBeVisible();
	});

	it('should call onDefinitionsChange with the parsed decision definition', async () => {
		const onDefinitionsChange = vi.fn();

		await render(
			<DecisionViewer xml={DMN_XML} decisionViewId="invoiceClassification" onDefinitionsChange={onDefinitionsChange} />,
		);

		await expect.poll(() => onDefinitionsChange.mock.calls.length).toBeGreaterThan(0);
		expect(onDefinitionsChange).toHaveBeenCalledWith(
			expect.objectContaining({id: 'invoiceBusinessDecisions', name: 'Invoice Business Decisions'}),
		);
	});

	it('should generate a highlight rule for the configured rule row', async () => {
		const screen = await render(
			<DecisionViewer xml={DMN_XML} decisionViewId="invoiceClassification" highlightableRules={[1]} />,
		);

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();

		const styleContent = Array.from(document.querySelectorAll('style'))
			.map((style) => style.textContent)
			.join('\n');

		expect(styleContent).toContain('tr:nth-child(1)');
	});
});
