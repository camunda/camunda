/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createDecisionDefinition} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DecisionHeader} from './DecisionHeader';

describe('<DecisionHeader />', () => {
	it('shows a generic title when there is no matching definition', async () => {
		const screen = await render(<DecisionHeader decisionDefinitionSelection={{kind: 'no-match'}} />);

		await expect.element(screen.getByText('Decision')).toBeVisible();
	});

	it('shows the decision name and copiable ID for a single selected version', async () => {
		const screen = await render(
			<DecisionHeader
				decisionDefinitionSelection={{
					kind: 'single-version',
					definition: createDecisionDefinition({
						name: 'Invoice Classification',
						decisionDefinitionId: 'invoice-classification',
					}),
				}}
			/>,
		);

		await expect.element(screen.getByText('Invoice Classification')).toBeVisible();
		await expect.element(screen.getByText('invoice-classification')).toBeVisible();
	});

	it('shows the decision name and copiable ID for an all-versions selection', async () => {
		const screen = await render(
			<DecisionHeader
				decisionDefinitionSelection={{
					kind: 'all-versions',
					definition: {name: 'Invoice Classification', decisionDefinitionId: 'invoice-classification'},
				}}
			/>,
		);

		await expect.element(screen.getByText('Invoice Classification')).toBeVisible();
		await expect.element(screen.getByText('invoice-classification')).toBeVisible();
	});
});
