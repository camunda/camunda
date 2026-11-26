/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.11';
import i18n from 'i18next';
import {it} from '#/vitest-modules/test-extend';
import {BatchStateIndicator} from './index';

describe('<BatchStateIndicator />', () => {
	afterEach(async () => {
		await i18n.changeLanguage('en');
	});

	it('should expose a batch-operation-specific status label by default', async () => {
		const screen = await render(<BatchStateIndicator state="COMPLETED" />);

		await expect.element(screen.getByRole('status', {name: 'Batch operation status: Completed'})).toBeVisible();
	});

	it('should expose an item-specific status label when used for item states', async () => {
		const screen = await render(<BatchStateIndicator state="FAILED" ariaLabelPrefix="Item status" />);

		await expect.element(screen.getByRole('status', {name: 'Item status: Failed'})).toBeVisible();
	});

	it('should show the raw status of a state the client does not know', async () => {
		const screen = await render(<BatchStateIndicator state={'UNKNOWN_STATE' as BatchOperationState} />);

		await expect
			.element(screen.getByRole('status', {name: 'Batch operation status: UNKNOWN_STATE'}))
			.toHaveTextContent('UNKNOWN_STATE');
	});

	it('should translate state labels and aria labels when language changes', async () => {
		await i18n.changeLanguage('de');

		const screen = await render(<BatchStateIndicator state="FAILED" />);

		await expect.element(screen.getByRole('status', {name: 'Status des Stapelvorgangs: Fehlgeschlagen'})).toBeVisible();
	});
});
