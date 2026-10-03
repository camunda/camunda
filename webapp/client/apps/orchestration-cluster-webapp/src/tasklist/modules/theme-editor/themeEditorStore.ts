/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, computed, makeObservable, observable} from 'mobx';
import {buildThemeTokens} from './buildThemeTokens';
import {toCustomCss, toPreviewCss} from './generateCss';
import {BASE_COLORS, DEFAULT_THEME_CONFIG, HUES, RADII, type ThemeConfig} from './presets';

type PreviewDataset = 'sample' | 'empty';

function pickRandom<T>(values: readonly T[], current: T): T {
	const candidates = values.filter((value) => value !== current);

	return candidates[Math.floor(Math.random() * candidates.length)] ?? current;
}

class ThemeEditorStore {
	config: ThemeConfig = {...DEFAULT_THEME_CONFIG};
	/** Temporary selection while hovering over a picker option. Never exported. */
	override: Partial<ThemeConfig> | null = null;
	dataset: PreviewDataset = 'sample';

	constructor() {
		makeObservable(this, {
			config: observable,
			override: observable,
			dataset: observable,
			previewConfig: computed,
			isDefault: computed,
			previewCss: computed,
			customCss: computed,
			setConfig: action,
			setOverride: action,
			clearOverride: action,
			setDataset: action,
			shuffle: action,
			reset: action,
		});
	}

	get previewConfig(): ThemeConfig {
		return this.override === null ? this.config : {...this.config, ...this.override};
	}

	get isDefault() {
		return (Object.keys(DEFAULT_THEME_CONFIG) as (keyof ThemeConfig)[]).every(
			(key) => this.config[key] === DEFAULT_THEME_CONFIG[key],
		);
	}

	get previewCss() {
		return toPreviewCss(buildThemeTokens(this.previewConfig));
	}

	get customCss() {
		return toCustomCss(buildThemeTokens(this.config), this.config);
	}

	setConfig = (config: Partial<ThemeConfig>) => {
		this.config = {...this.config, ...config};
		this.override = null;
	};

	setOverride = (override: Partial<ThemeConfig>) => {
		this.override = override;
	};

	clearOverride = () => {
		this.override = null;
	};

	setDataset = (dataset: PreviewDataset) => {
		this.dataset = dataset;
	};

	shuffle = () => {
		const primary = Math.random() < 0.2 ? 'default' : pickRandom(['camunda', ...HUES] as const, this.config.primary);

		this.setConfig({
			base: pickRandom(BASE_COLORS, this.config.base),
			primary,
			accent: pickRandom(HUES, this.config.accent),
			radius: pickRandom(RADII, this.config.radius),
		});
	};

	reset = () => {
		this.setConfig({...DEFAULT_THEME_CONFIG});
	};
}

const themeEditorStore = new ThemeEditorStore();

export {themeEditorStore};
export type {PreviewDataset};
