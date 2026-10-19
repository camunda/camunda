/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import colors from 'tailwindcss/colors';
import {z} from 'zod';
import {buildColorRamp, buildNeutralRamp, isHexColor, type HexColor, type Palette} from './colorRamp';

type PresetOption<Value extends string> = {
	value: Value;
	label: string;
	/** Color shown next to the option in the picker. */
	swatch: string;
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

type PrimaryColor = 'default' | Hue;
type AccentColor = Hue;

/** The radius, in rem, that the editor's 100% stands for. */
const MAX_RADIUS_REM = 1.5;

function radiusToPercent(radius: string) {
	return Math.min(100, Math.round((parseFloat(radius) / MAX_RADIUS_REM) * 100));
}

function percentToRadius(percent: number) {
	return `${Number(((percent / 100) * MAX_RADIUS_REM).toFixed(3))}rem`;
}

/** Each color is either a preset or a custom `#rrggbb` value. */
type ThemeConfig = {
	base: BaseColor | HexColor;
	primary: PrimaryColor | HexColor;
	accent: AccentColor | HexColor;
	radius: string;
	/** An uploaded logo as a `data:` URL, or `null` for the Camunda logo. */
	logo: string | null;
};

/** Matches the design system's shipped values. */
const DEFAULT_THEME_CONFIG: ThemeConfig = {
	base: 'zinc',
	primary: 'default',
	accent: 'indigo',
	radius: '0.5rem',
	logo: null,
};

/** Validates a stored config, so a stale or edited value can never reach the token builder. */
const themeConfigSchema = z.object({
	base: z.union([z.enum(BASE_COLORS), z.custom<HexColor>((value) => typeof value === 'string' && isHexColor(value))]),
	primary: z.union([
		z.literal('default'),
		z.enum(HUES),
		z.custom<HexColor>((value) => typeof value === 'string' && isHexColor(value)),
	]),
	accent: z.union([z.enum(HUES), z.custom<HexColor>((value) => typeof value === 'string' && isHexColor(value))]),
	radius: z.string().regex(/^\d*\.?\d+(rem)?$/),
	logo: z.string().startsWith('data:image/').nullable().default(null),
});

function capitalize(value: string) {
	return value.charAt(0).toUpperCase() + value.slice(1);
}

/** `neutral` ramps are for surfaces and text. Custom colors only tint them. */
function getPalette(color: BaseColor | Hue | HexColor, role: 'neutral' | 'color' = 'color'): Palette {
	if (isHexColor(color)) {
		return role === 'neutral' ? buildNeutralRamp(color) : buildColorRamp(color);
	}

	return colors[color];
}

const BASE_COLOR_OPTIONS: PresetOption<BaseColor>[] = BASE_COLORS.map((value) => ({
	value,
	label: capitalize(value),
	swatch: getPalette(value)['500'],
}));

const PRIMARY_COLOR_OPTIONS: PresetOption<PrimaryColor>[] = [
	{value: 'default', label: 'Default', swatch: colors.zinc['950']},
	...HUES.map((value) => ({value, label: capitalize(value), swatch: getPalette(value)['700']})),
];

const ACCENT_COLOR_OPTIONS: PresetOption<AccentColor>[] = HUES.map((value) => ({
	value,
	label: capitalize(value),
	swatch: getPalette(value)['700'],
}));

export {
	ACCENT_COLOR_OPTIONS,
	BASE_COLOR_OPTIONS,
	DEFAULT_THEME_CONFIG,
	PRIMARY_COLOR_OPTIONS,
	getPalette,
	percentToRadius,
	radiusToPercent,
	themeConfigSchema,
};
export type {PresetOption, ThemeConfig};
