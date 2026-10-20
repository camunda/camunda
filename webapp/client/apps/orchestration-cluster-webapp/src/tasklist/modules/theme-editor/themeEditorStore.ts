/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, computed, makeObservable, observable} from 'mobx';
import {getAppliedTheme, saveAppliedTheme} from './appliedTheme';
import {buildAllTokens, buildThemeTokens} from './buildThemeTokens';
import {toCustomCss, toPreviewCss} from './generateCss';
import {DEFAULT_THEME_CONFIG, type ThemeConfig} from './presets';

type PreviewDataset = 'sample' | 'empty';

function isSameConfig(first: ThemeConfig, second: ThemeConfig) {
	return (Object.keys(DEFAULT_THEME_CONFIG) as (keyof ThemeConfig)[]).every((key) => first[key] === second[key]);
}

class ThemeEditorStore {
	/** The theme applied to the app in this browser, or `null` for the default theme. */
	applied: ThemeConfig | null = getAppliedTheme();
	config: ThemeConfig = {...(this.applied ?? DEFAULT_THEME_CONFIG)};
	dataset: PreviewDataset = 'sample';

	constructor() {
		makeObservable(this, {
			applied: observable,
			config: observable,
			dataset: observable,
			isDefault: computed,
			hasUnappliedChanges: computed,
			previewCss: computed,
			customCss: computed,
			setConfig: action,
			applyTheme: action,
			reset: action,
		});
	}

	get isDefault() {
		return isSameConfig(this.config, DEFAULT_THEME_CONFIG);
	}

	get hasUnappliedChanges() {
		return !isSameConfig(this.config, this.applied ?? DEFAULT_THEME_CONFIG);
	}

	get previewCss() {
		return toPreviewCss(buildAllTokens(this.config));
	}

	get customCss() {
		return toCustomCss(buildThemeTokens(this.config), this.config);
	}

	setConfig = (config: Partial<ThemeConfig>) => {
		this.config = {...this.config, ...config};
	};

	applyTheme = () => {
		this.applied = this.isDefault ? null : {...this.config};
		saveAppliedTheme(this.applied);
	};

	/** Returns the editor to the default theme and removes an applied theme. */
	reset = () => {
		this.setConfig({...DEFAULT_THEME_CONFIG});
		this.applied = null;
		saveAppliedTheme(null);
	};
}

const themeEditorStore = new ThemeEditorStore();

export {themeEditorStore};
