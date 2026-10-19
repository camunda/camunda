/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type Shade = '50' | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900' | '950';
type Palette = Record<Shade, string>;
type HexColor = `#${string}`;

type Oklch = {l: number; c: number; h: number};

const SHADES: Shade[] = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'];

/*
 * Lightness and chroma curves of the Tailwind ramps the presets use. A custom color is slotted in
 * at shade 700, the shade the design system uses for action colors, and the other shades follow
 * the same curve around it.
 */
const COLOR_LIGHTNESS: Record<Shade, number> = {
	'50': 0.97,
	'100': 0.94,
	'200': 0.89,
	'300': 0.81,
	'400': 0.71,
	'500': 0.62,
	'600': 0.55,
	'700': 0.49,
	'800': 0.42,
	'900': 0.36,
	'950': 0.27,
};

const COLOR_CHROMA: Record<Shade, number> = {
	'50': 0.1,
	'100': 0.2,
	'200': 0.4,
	'300': 0.65,
	'400': 0.85,
	'500': 1,
	'600': 1,
	'700': 1,
	'800': 0.9,
	'900': 0.75,
	'950': 0.6,
};

const NEUTRAL_LIGHTNESS: Record<Shade, number> = {
	'50': 0.985,
	'100': 0.967,
	'200': 0.92,
	'300': 0.871,
	'400': 0.705,
	'500': 0.552,
	'600': 0.442,
	'700': 0.37,
	'800': 0.274,
	'900': 0.21,
	'950': 0.141,
};

/** The most color a neutral ramp carries, so a tinted base still reads as grey. */
const MAX_NEUTRAL_CHROMA = 0.03;

const HEX_PATTERN = /^#[0-9a-f]{6}$/i;
const OKLCH_PATTERN = /^oklch\(\s*([\d.]+)(%?)\s+([\d.]+)\s+([\d.]+)/i;

/** Accepts `#rgb`, `#rrggbb`, with or without the leading `#`. Returns `null` for anything else. */
function normalizeHex(input: string): HexColor | null {
	const value = input.trim().replace(/^#/, '').toLowerCase();

	if (/^[0-9a-f]{3}$/.test(value)) {
		return `#${value[0]}${value[0]}${value[1]}${value[1]}${value[2]}${value[2]}`;
	}

	return /^[0-9a-f]{6}$/.test(value) ? `#${value}` : null;
}

function isHexColor(value: string): value is HexColor {
	return HEX_PATTERN.test(value);
}

function srgbToLinear(channel: number) {
	return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
}

function linearToSrgb(channel: number) {
	return channel <= 0.0031308 ? channel * 12.92 : 1.055 * channel ** (1 / 2.4) - 0.055;
}

function hexToRgb(hex: HexColor): [number, number, number] {
	return [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16) / 255) as [number, number, number];
}

function rgbToOklch([red, green, blue]: [number, number, number]): Oklch {
	const [r, g, b] = [red, green, blue].map(srgbToLinear) as [number, number, number];
	const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
	const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
	const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
	const lightness = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
	const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
	const bAxis = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

	return {l: lightness, c: Math.hypot(a, bAxis), h: ((Math.atan2(bAxis, a) * 180) / Math.PI + 360) % 360};
}

/** Linear sRGB for an OKLCH color. Channels outside 0 to 1 are outside the sRGB gamut. */
function oklchToLinearRgb({l, c, h}: Oklch): [number, number, number] {
	const a = c * Math.cos((h * Math.PI) / 180);
	const b = c * Math.sin((h * Math.PI) / 180);
	const lCube = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3;
	const mCube = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3;
	const sCube = (l - 0.0894841775 * a - 1.291485548 * b) ** 3;

	return [
		4.0767416621 * lCube - 3.3077115913 * mCube + 0.2309699292 * sCube,
		-1.2684380046 * lCube + 2.6097574011 * mCube - 0.3413193965 * sCube,
		-0.0041960863 * lCube - 0.7034186147 * mCube + 1.707614701 * sCube,
	];
}

function isInGamut(channels: number[]) {
	return channels.every((channel) => channel >= -0.0005 && channel <= 1.0005);
}

function oklchToHex(color: Oklch): HexColor {
	let {c} = color;
	let channels = oklchToLinearRgb({...color, c});

	// Brighter or darker steps of a vivid color can leave sRGB, so lower the chroma until they fit.
	for (let step = 0; step < 24 && !isInGamut(channels); step++) {
		c *= 0.92;
		channels = oklchToLinearRgb({...color, c});
	}

	const hex = channels
		.map((channel) => Math.round(Math.min(1, Math.max(0, linearToSrgb(Math.min(1, Math.max(0, channel))))) * 255))
		.map((value) => value.toString(16).padStart(2, '0'))
		.join('');

	return `#${hex}`;
}

/** Reads `#rrggbb` and the `oklch(L% C H)` strings the Tailwind palette uses. */
function parseColor(color: string): Oklch | null {
	if (isHexColor(color)) {
		return rgbToOklch(hexToRgb(color));
	}

	const match = OKLCH_PATTERN.exec(color);

	if (match === null) {
		return null;
	}

	const lightness = Number(match[1]);

	return {l: match[2] === '%' ? lightness / 100 : lightness, c: Number(match[3]), h: Number(match[4])};
}

/** Returns a `#rrggbb` color, or `null` when the value cannot be read. */
function toHexColor(color: string): HexColor | null {
	if (isHexColor(color)) {
		return color.toLowerCase() as HexColor;
	}

	const parsed = parseColor(color);

	return parsed === null ? null : oklchToHex(parsed);
}

function relativeLuminance(hex: HexColor) {
	const [r, g, b] = hexToRgb(hex).map(srgbToLinear) as [number, number, number];

	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(first: HexColor, second: HexColor) {
	const [lighter, darker] = [relativeLuminance(first), relativeLuminance(second)].sort((a, b) => b - a) as [
		number,
		number,
	];

	return (lighter + 0.05) / (darker + 0.05);
}

const LIGHT_FOREGROUND: HexColor = '#ffffff';
const DARK_FOREGROUND: HexColor = '#09090b';

/** White or near-black text, whichever reads better on the given color. */
function getReadableForeground(color: string): HexColor {
	const hex = toHexColor(color);

	if (hex === null) {
		return LIGHT_FOREGROUND;
	}

	return contrastRatio(hex, LIGHT_FOREGROUND) >= contrastRatio(hex, DARK_FOREGROUND)
		? LIGHT_FOREGROUND
		: DARK_FOREGROUND;
}

/** A full ramp for a brand color. Shade 700 is the color itself. */
function buildColorRamp(hex: HexColor): Palette {
	const brand = rgbToOklch(hexToRgb(hex));
	const anchor = COLOR_LIGHTNESS['700'];

	return Object.fromEntries(
		SHADES.map((shade) => {
			if (shade === '700') {
				return [shade, hex];
			}

			const offset = COLOR_LIGHTNESS[shade] - anchor;
			// Keep the ramp ordered, whatever the lightness of the brand color.
			const lightness =
				offset > 0 ? brand.l + (offset * (1 - brand.l)) / (1 - anchor) : brand.l + (offset * brand.l) / anchor;

			return [shade, oklchToHex({l: lightness, c: brand.c * COLOR_CHROMA[shade], h: brand.h})];
		}),
	) as Palette;
}

/** A grey ramp with a hint of the given color's hue, for surfaces, borders and text. */
function buildNeutralRamp(hex: HexColor): Palette {
	const tint = rgbToOklch(hexToRgb(hex));
	const chroma = Math.min(tint.c, MAX_NEUTRAL_CHROMA);

	return Object.fromEntries(
		SHADES.map((shade) => [shade, oklchToHex({l: NEUTRAL_LIGHTNESS[shade], c: chroma, h: tint.h})]),
	) as Palette;
}

export {buildColorRamp, buildNeutralRamp, getReadableForeground, isHexColor, normalizeHex, toHexColor};
export type {HexColor, Palette};
