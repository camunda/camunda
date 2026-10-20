/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DEFAULT_THEME_CONFIG, getPalette, type Palette, type ThemeConfig} from './presets';

type TokenMap = Record<string, string>;

type ThemeTokens = {
	/** Applied with the light selector. Tokens only defined here (such as `--radius`) apply to both themes. */
	light: TokenMap;
	dark: TokenMap;
};

const WHITE = '#ffffff';

function mix(color: string, percentage: number, other = 'transparent') {
	return `color-mix(in srgb, ${color} ${percentage}%, ${other})`;
}

/*
 * The builders below mirror the `.c4-ui` (light) and `.c4-ui.dark` blocks in the design
 * system's `src/index.css`, with the zinc ramp swapped for the base palette and the indigo
 * ramp swapped for the accent palette. Values are literal colors rather than `var(--color-*)`
 * references, because Tailwind strips unused palette variables from the build.
 */

function buildLightTokens(neutral: Palette, primary: Palette | null, accent: Palette, radius: string): TokenMap {
	const primaryTokens: TokenMap =
		primary === null
			? {
					'--primary-action-default': neutral['950'],
					'--primary-action-hover': neutral['800'],
					'--primary-action-active': mix(neutral['800'], 88, 'black'),
					'--primary-action-disabled': mix(neutral['950'], 38),
					'--primary-action-foreground': WHITE,
					'--primary-background-subtle': neutral['100'],
					'--primary-background-strong': neutral['200'],
					'--primary-foreground-subtle': neutral['700'],
					'--primary-foreground-strong': neutral['900'],
					'--primary-border-subtle': neutral['200'],
					'--primary-border-strong': neutral['400'],
				}
			: {
					'--primary-action-default': primary['700'],
					'--primary-action-hover': primary['800'],
					'--primary-action-active': primary['900'],
					'--primary-action-disabled': mix(primary['700'], 38),
					'--primary-action-foreground': WHITE,
					'--primary-background-subtle': primary['50'],
					'--primary-background-strong': primary['100'],
					'--primary-foreground-subtle': primary['700'],
					'--primary-foreground-strong': primary['900'],
					'--primary-border-subtle': primary['200'],
					'--primary-border-strong': primary['400'],
				};

	return {
		'--background': neutral['50'],
		'--foreground': neutral['900'],
		'--border': mix(neutral['400'], 20),
		'--radius': radius,
		'--popover': WHITE,
		'--popover-foreground': neutral['900'],
		'--input': neutral['300'],
		'--input-background': WHITE,
		'--switch-background': neutral['300'],

		'--neutral-action-default': neutral['700'],
		'--neutral-action-hover': neutral['800'],
		'--neutral-action-active': neutral['900'],
		'--neutral-action-disabled': mix(neutral['700'], 38),
		'--neutral-action-foreground': neutral['50'],
		'--neutral-background-subtle': WHITE,
		'--neutral-background-medium': mix(neutral['100'], 80, neutral['200']),
		'--neutral-background-strong': neutral['200'],
		'--neutral-foreground-subtle': neutral['600'],
		'--neutral-foreground-strong': neutral['800'],
		'--neutral-border-subtle': neutral['200'],
		'--neutral-border-strong': neutral['300'],

		...primaryTokens,

		'--accent-action-default': accent['700'],
		'--accent-action-hover': accent['800'],
		'--accent-action-active': accent['900'],
		'--accent-action-disabled': mix(accent['700'], 38),
		'--accent-action-foreground': WHITE,
		'--accent-background-subtle': accent['50'],
		'--accent-background-strong': accent['100'],
		'--accent-foreground-subtle': accent['700'],
		'--accent-foreground-strong': accent['900'],
		'--accent-border-subtle': accent['200'],
		'--accent-border-strong': accent['400'],
	};
}

function buildDarkTokens(neutral: Palette, primary: Palette | null, accent: Palette): TokenMap {
	const primaryTokens: TokenMap =
		primary === null
			? {
					'--primary-action-default': WHITE,
					'--primary-action-hover': neutral['100'],
					'--primary-action-active': neutral['200'],
					'--primary-action-disabled': mix(WHITE, 38),
					'--primary-action-foreground': neutral['950'],
					'--primary-background-subtle': neutral['600'],
					'--primary-background-strong': neutral['700'],
					'--primary-foreground-subtle': neutral['200'],
					'--primary-foreground-strong': WHITE,
					'--primary-border-subtle': neutral['300'],
					'--primary-border-strong': neutral['500'],
				}
			: {
					'--primary-action-default': primary['400'],
					'--primary-action-hover': primary['300'],
					'--primary-action-active': primary['200'],
					'--primary-action-disabled': mix(primary['400'], 38),
					'--primary-action-foreground': neutral['950'],
					'--primary-background-subtle': mix(primary['500'], 14, neutral['900']),
					'--primary-background-strong': mix(primary['500'], 22, neutral['800']),
					'--primary-foreground-subtle': primary['300'],
					'--primary-foreground-strong': primary['100'],
					'--primary-border-subtle': primary['800'],
					'--primary-border-strong': primary['600'],
				};

	return {
		'--background': neutral['950'],
		'--foreground': neutral['50'],
		'--border': mix(WHITE, 16),
		'--popover': neutral['800'],
		'--popover-foreground': neutral['50'],
		'--input': neutral['400'],
		'--input-background': neutral['900'],
		'--switch-background': neutral['700'],

		'--neutral-action-default': neutral['700'],
		'--neutral-action-hover': neutral['800'],
		'--neutral-action-active': neutral['900'],
		'--neutral-action-disabled': mix(neutral['700'], 38),
		'--neutral-action-foreground': neutral['50'],
		'--neutral-background-subtle': neutral['900'],
		'--neutral-background-medium': neutral['800'],
		'--neutral-background-strong': neutral['700'],
		'--neutral-foreground-subtle': neutral['300'],
		'--neutral-foreground-strong': neutral['50'],
		'--neutral-border-subtle': neutral['700'],
		'--neutral-border-strong': neutral['500'],

		...primaryTokens,

		'--accent-action-default': accent['700'],
		'--accent-action-hover': accent['800'],
		'--accent-action-active': accent['900'],
		'--accent-action-disabled': mix(accent['700'], 38),
		'--accent-action-foreground': WHITE,
		'--accent-background-subtle': mix(accent['500'], 14, neutral['900']),
		'--accent-background-strong': mix(accent['500'], 22, neutral['800']),
		'--accent-foreground-subtle': accent['400'],
		'--accent-foreground-strong': accent['200'],
		'--accent-border-subtle': accent['800'],
		'--accent-border-strong': accent['600'],
	};
}

function buildAllTokens({base, primary, accent, radius}: ThemeConfig): ThemeTokens {
	const neutralPalette = getPalette(base);
	const primaryPalette = primary === 'default' ? null : getPalette(primary);
	// The brand's action color is brand-600, one step lighter than the hue presets use.
	const lightPrimaryPalette =
		primary === 'camunda' && primaryPalette !== null
			? {...primaryPalette, '700': primaryPalette['600'], '800': primaryPalette['700'], '900': primaryPalette['800']}
			: primaryPalette;
	const accentPalette = getPalette(accent);

	return {
		light: buildLightTokens(neutralPalette, lightPrimaryPalette, accentPalette, radius),
		dark: buildDarkTokens(neutralPalette, primaryPalette, accentPalette),
	};
}

function diffTokens(tokens: TokenMap, defaults: TokenMap): TokenMap {
	return Object.fromEntries(Object.entries(tokens).filter(([name, value]) => defaults[name] !== value));
}

const DEFAULT_TOKENS = buildAllTokens(DEFAULT_THEME_CONFIG);

/** Returns only the tokens that differ from the design system defaults. */
function buildThemeTokens(config: ThemeConfig): ThemeTokens {
	const tokens = buildAllTokens(config);

	return {
		light: diffTokens(tokens.light, DEFAULT_TOKENS.light),
		dark: diffTokens(tokens.dark, DEFAULT_TOKENS.dark),
	};
}

export {buildThemeTokens};
export type {ThemeTokens, TokenMap};
