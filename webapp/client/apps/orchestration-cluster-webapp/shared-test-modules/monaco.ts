/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {userEvent, type Locator} from 'vitest/browser';

// Monaco's textbox is an EditContext div, which `userEvent.fill` cannot write to.
async function replaceMonacoValue(editor: Locator, value: string) {
	// Monaco's view lines overlay the textbox, so a pointer click on it is intercepted.
	editor.element().focus();
	await userEvent.keyboard('{Control>}a{/Control}{Meta>}a{/Meta}{Backspace}');
	await userEvent.keyboard(value.replaceAll('{', '{{').replaceAll('[', '[['));
}

export {replaceMonacoValue};
