/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {MigrationConfirmationModal} from './MigrationConfirmationModal';

const SOURCE = createProcessDefinition({processDefinitionKey: 'source-key', name: 'Invoice process', version: 1});
const TARGET = createProcessDefinition({processDefinitionKey: 'target-key', name: 'Invoice process', version: 2});
const SCOPE = {
	filter: {processDefinitionKey: {$eq: 'source-key'}},
	statisticsFilter: {},
	selectedCount: 2,
	isCountTruncated: false,
};
function renderModal(hasElementMapping: boolean, scope = SCOPE) {
	return render(
		<MigrationConfirmationModal
			source={SOURCE}
			target={TARGET}
			scope={scope}
			hasElementMapping={hasElementMapping}
			onClose={() => {}}
			onSubmit={() => {}}
		/>,
	);
}

describe('<MigrationConfirmationModal />', () => {
	it.for([
		{mapping: 'with mapped elements', hasElementMapping: true, expected: 'enabled'},
		{mapping: 'without mapped elements', hasElementMapping: false, expected: 'disabled'},
	] as const)('should keep Confirm $expected after typing MIGRATE $mapping', async ({hasElementMapping, expected}) => {
		const screen = await renderModal(hasElementMapping);
		await expect.element(screen.getByText(/^You are about to migrate 2 process instances /)).toBeVisible();

		await userEvent.fill(screen.getByRole('textbox'), 'MIGRATE');

		const confirm = screen.getByRole('button', {name: 'Confirm'});
		if (expected === 'enabled') {
			await expect.element(confirm).toBeEnabled();
		} else {
			await expect.element(confirm).toBeDisabled();
		}
	});

	it('should state a truncated number of instances with a plus sign', async () => {
		const screen = await renderModal(true, {...SCOPE, isCountTruncated: true});

		await expect.element(screen.getByText(/^You are about to migrate 2\+ process instances /)).toBeVisible();
	});
});
