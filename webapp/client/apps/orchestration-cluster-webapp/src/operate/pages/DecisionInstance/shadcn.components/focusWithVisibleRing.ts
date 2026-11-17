/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const FOCUS_VISIBLE_RESTORE_ATTR = 'data-focus-visible-restore';

// A programmatic focus after a mouse interaction does not match :focus-visible, so the DS Button shows no ring.
// The DS Button also rings for this transient marker, which is cleared on blur or the next pointerdown.
const focusWithVisibleRing = (element: HTMLElement) => {
	element.focus();
	element.setAttribute(FOCUS_VISIBLE_RESTORE_ATTR, '');

	const clear = () => {
		element.removeAttribute(FOCUS_VISIBLE_RESTORE_ATTR);
		element.removeEventListener('blur', clear);
		document.removeEventListener('pointerdown', clear, true);
	};
	element.addEventListener('blur', clear);
	document.addEventListener('pointerdown', clear, true);
};

export {focusWithVisibleRing};
