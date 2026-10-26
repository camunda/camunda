/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {ForbiddenError} from '#/shared/errors';
import {DecisionInstanceError} from './DecisionInstanceError';

const DECISION_INSTANCE_ID = '4294980768';
const PATH = '/operate/decisions/$decisionInstanceId';
const INITIAL_ENTRY = `/operate/decisions/${DECISION_INSTANCE_ID}`;

describe('<DecisionInstanceError /> (design system)', () => {
	it('should focus the error heading when arriving from the DRD', async () => {
		const screen = await renderWithRouter(
			() => (
				<DecisionInstanceError
					error={new ForbiddenError()}
					decisionInstanceId={DECISION_INSTANCE_ID}
					onRetry={() => {}}
				/>
			),
			{path: PATH, initialEntry: INITIAL_ENTRY},
		);

		await screen.router.navigate({
			to: PATH,
			params: {decisionInstanceId: DECISION_INSTANCE_ID},
			state: {operateDecisionFocus: {decisionInstanceKey: DECISION_INSTANCE_ID}},
		});

		await expect
			.element(screen.getByRole('heading', {name: 'You do not have permission to view this information'}))
			.toHaveFocus();
	});
});
