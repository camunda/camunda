/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import colors from 'tailwindcss/colors';

type Shade = '50' | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900' | '950';
type Palette = Record<Shade, string>;

type PresetOption<Value extends string> = {
	value: Value;
	label: string;
	/** Color shown next to the option in the picker. */
	swatch: string;
};

/**
 * Camunda brand ramp, copied from the design system (`--color-brand-*`). Used for
 * the "Camunda orange" primary so the preset matches the brand exactly.
 */
const CAMUNDA_BRAND: Palette = {
	'50': '#fef3ee',
	'100': '#ffece3',
	'200': '#ffd7c3',
	'300': '#ffbba6',
	'400': '#ffa088',
	'500': '#fc5d0d',
	'600': '#c44500',
	'700': '#9c3700',
	'800': '#742900',
	'900': '#4c1b00',
	'950': '#240e00',
};

const BASE_COLORS = ['zinc', 'slate', 'gray', 'neutral', 'stone', 'mauve', 'olive', 'mist', 'taupe'] as const;
type BaseColor = (typeof BASE_COLORS)[number];

const HUES = [
	'indigo',
	'blue',
	'sky',
	'cyan',
	'teal',
	'emerald',
	'green',
	'violet',
	'purple',
	'fuchsia',
	'pink',
	'rose',
	'red',
	'orange',
] as const;
type Hue = (typeof HUES)[number];

type PrimaryColor = 'default' | 'camunda' | Hue;
type AccentColor = Hue;

const RADII = ['0', '0.25rem', '0.5rem', '0.75rem', '1rem'] as const;
type Radius = (typeof RADII)[number];

type ThemeConfig = {
	base: BaseColor;
	primary: PrimaryColor;
	accent: AccentColor;
	radius: Radius;
};

/** Matches the design system's shipped values. */
const DEFAULT_THEME_CONFIG: ThemeConfig = {
	base: 'zinc',
	primary: 'default',
	accent: 'indigo',
	radius: '0.5rem',
};

function capitalize(value: string) {
	return value.charAt(0).toUpperCase() + value.slice(1);
}

function getPalette(color: BaseColor | Hue | 'camunda'): Palette {
	return color === 'camunda' ? CAMUNDA_BRAND : colors[color];
}

const BASE_COLOR_OPTIONS: PresetOption<BaseColor>[] = BASE_COLORS.map((value) => ({
	value,
	label: capitalize(value),
	swatch: getPalette(value)['500'],
}));

const PRIMARY_COLOR_OPTIONS: PresetOption<PrimaryColor>[] = [
	{value: 'default', label: 'Default', swatch: colors.zinc['950']},
	{value: 'camunda', label: 'Camunda orange', swatch: CAMUNDA_BRAND['600']},
	...HUES.map((value) => ({value, label: capitalize(value), swatch: getPalette(value)['700']})),
];

const ACCENT_COLOR_OPTIONS: PresetOption<AccentColor>[] = HUES.map((value) => ({
	value,
	label: capitalize(value),
	swatch: getPalette(value)['700'],
}));

const RADIUS_OPTIONS: PresetOption<Radius>[] = [
	{value: '0', label: 'None', swatch: '0'},
	{value: '0.25rem', label: 'Small', swatch: '0.25rem'},
	{value: '0.5rem', label: 'Default', swatch: '0.5rem'},
	{value: '0.75rem', label: 'Medium', swatch: '0.75rem'},
	{value: '1rem', label: 'Large', swatch: '1rem'},
];

export {
	ACCENT_COLOR_OPTIONS,
	BASE_COLOR_OPTIONS,
	BASE_COLORS,
	DEFAULT_THEME_CONFIG,
	HUES,
	PRIMARY_COLOR_OPTIONS,
	RADII,
	RADIUS_OPTIONS,
	getPalette,
};
export type {Palette, PresetOption, ThemeConfig};
