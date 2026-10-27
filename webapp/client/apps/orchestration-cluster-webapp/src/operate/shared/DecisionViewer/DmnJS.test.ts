/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi} from 'vitest';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {DmnJS} from './DmnJS';

describe('DmnJS', () => {
	it('should not throw when reset while the xml import is pending', async () => {
		// given
		const dmnJS = new DmnJS();
		const onDefinitionsChange = vi.fn();
		dmnJS.onDefinitionsChange = onDefinitionsChange;
		const container = document.createElement('div');
		document.body.appendChild(container);

		// when
		const rendering = dmnJS.render(
			container,
			DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE,
			'invoiceClassification',
		);
		dmnJS.reset();

		// then
		await expect(rendering).resolves.toBeUndefined();
		expect(onDefinitionsChange).not.toHaveBeenCalled();

		container.remove();
	});
});
