/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import colors from 'tailwindcss/colors';
import {z} from 'zod';
import {buildColorRamp, buildNeutralRamp, isHexColor, toHexColor, type HexColor, type Palette} from './colorRamp';

/** The radius, in rem, that the editor's 100% stands for. */
const MAX_RADIUS_REM = 1.5;

function radiusToPercent(radius: string) {
	return Math.min(100, Math.round((parseFloat(radius) / MAX_RADIUS_REM) * 100));
}

function percentToRadius(percent: number) {
	return `${Number(((percent / 100) * MAX_RADIUS_REM).toFixed(3))}rem`;
}

const ENVIRONMENTS = ['dev', 'test', 'stage', 'production'] as const;
type Environment = (typeof ENVIRONMENTS)[number];

/**
 * Each color is the design system's own color, until the admin enters a `#rrggbb` hex. The
 * defaults are only names for what the design system ships, so an untouched theme exports nothing.
 */
type ThemeConfig = {
	base: 'zinc' | HexColor;
	primary: 'default' | HexColor;
	accent: 'indigo' | HexColor;
	radius: string;
	/** An uploaded logo as a `data:` URL, or `null` for the Camunda logo. */
	logo: string | null;
	/** An environment tag to show in the header, or `null` for none. */
	environment: Environment | null;
};

/** Matches the design system's shipped values. */
const DEFAULT_THEME_CONFIG: ThemeConfig = {
	base: 'zinc',
	primary: 'default',
	accent: 'indigo',
	radius: '0.5rem',
	logo: null,
	environment: null,
};

const hexSchema = z.custom<HexColor>((value) => typeof value === 'string' && isHexColor(value));

/** Validates a stored config, so a stale or edited value can never reach the token builder. */
const themeConfigSchema = z.object({
	base: z.union([z.literal('zinc'), hexSchema]),
	primary: z.union([z.literal('default'), hexSchema]),
	accent: z.union([z.literal('indigo'), hexSchema]),
	radius: z.string().regex(/^\d*\.?\d+(rem)?$/),
	logo: z.string().startsWith('data:image/').nullable().default(null),
	environment: z.enum(ENVIRONMENTS).nullable().default(null),
});

/** `neutral` ramps are for surfaces and text. Custom colors only tint them. */
function getPalette(color: 'zinc' | 'indigo' | HexColor, role: 'neutral' | 'color' = 'color'): Palette {
	if (isHexColor(color)) {
		return role === 'neutral' ? buildNeutralRamp(color) : buildColorRamp(color);
	}

	return colors[color];
}

/** The hex shown for each default color: the shade the design system uses for it. */
const DEFAULT_COLOR_HEX = {
	zinc: colors.zinc['500'],
	default: colors.zinc['950'],
	indigo: colors.indigo['700'],
};

/** The hex to show in a color field, for a custom hex or one of the defaults. */
function getDisplayHex(color: ThemeConfig['base'] | ThemeConfig['primary'] | ThemeConfig['accent']): HexColor {
	return isHexColor(color) ? color : (toHexColor(DEFAULT_COLOR_HEX[color]) ?? '#000000');
}

export {
	DEFAULT_THEME_CONFIG,
	ENVIRONMENTS,
	getDisplayHex,
	getPalette,
	percentToRadius,
	radiusToPercent,
	themeConfigSchema,
};
export type {Environment, ThemeConfig};
