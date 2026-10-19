/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {clearStateLocally, getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {buildThemeTokens} from './buildThemeTokens';
import {toCustomCss} from './generateCss';
import type {ThemeConfig} from './presets';

const STYLE_ELEMENT_ID = 'tasklist-applied-theme';
const STORAGE_KEY = 'tasklist.appliedTheme';

function getAppliedTheme(): ThemeConfig | null {
	return getStateLocally(STORAGE_KEY);
}

/** Puts the theme's tokens on the whole app, the same way the server's `custom.css` does. */
function injectAppliedTheme(config: ThemeConfig | null) {
	document.getElementById(STYLE_ELEMENT_ID)?.remove();

	if (config === null) {
		return;
	}

	const style = document.createElement('style');

	style.id = STYLE_ELEMENT_ID;
	style.textContent = toCustomCss(buildThemeTokens(config), config);
	document.head.appendChild(style);
}

/** Keeps the theme in this browser and applies it. `null` removes it. */
function saveAppliedTheme(config: ThemeConfig | null) {
	if (config === null) {
		clearStateLocally(STORAGE_KEY);
	} else {
		storeStateLocally(STORAGE_KEY, config);
	}

	injectAppliedTheme(config);
}

export {getAppliedTheme, injectAppliedTheme, saveAppliedTheme};
