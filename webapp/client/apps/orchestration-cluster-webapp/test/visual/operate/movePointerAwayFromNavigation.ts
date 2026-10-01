/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page} from '@playwright/test';

const movePointerAwayFromNavigation = async (page: Page) => {
	const viewport = page.viewportSize();
	if (viewport === null) {
		throw new Error('Visual test requires a browser viewport');
	}
	await page.mouse.move(viewport.width - 1, viewport.height - 1);
};

export {movePointerAwayFromNavigation};
