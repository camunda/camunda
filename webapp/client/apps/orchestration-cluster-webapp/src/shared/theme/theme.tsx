/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, computed, makeObservable, observable} from 'mobx';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {getBootConfig} from '#/shared/config/getBootConfig';

type ThemeOption = 'system' | 'dark' | 'light';

function isThemeOption(theme: unknown): theme is ThemeOption {
	return ['system', 'dark', 'light'].includes(String(theme));
}

function getInitialTheme(): ThemeOption {
	const {organizationId, clusterId} = getBootConfig();
	const isSaas = organizationId !== null && clusterId !== null;
	const storedTheme = getStateLocally('themeChoice') ?? (isSaas ? null : getStateLocally('theme'));

	return isThemeOption(storedTheme) ? storedTheme : 'system';
}

const INITIAL_THEME = getInitialTheme();

class Theme {
	selectedTheme: ThemeOption = INITIAL_THEME;
	#systemDefault: 'dark' | 'light' = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

	constructor() {
		makeObservable(this, {
			selectedTheme: observable,
			changeTheme: action,
			seedTheme: action,
			actualTheme: computed,
			reset: action,
		});
	}

	changeTheme = (theme: ThemeOption) => {
		this.selectedTheme = theme;
		storeStateLocally('themeChoice', theme);
	};

	seedTheme = (theme: ThemeOption) => {
		this.selectedTheme = theme;
	};

	get actualTheme() {
		return this.selectedTheme === 'system' ? this.#systemDefault : this.selectedTheme;
	}

	reset = () => {
		this.selectedTheme = INITIAL_THEME;
	};
}

const themeStore = new Theme();

export {themeStore, getInitialTheme};
