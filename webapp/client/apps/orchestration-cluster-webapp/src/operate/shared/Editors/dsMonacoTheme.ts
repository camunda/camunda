/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Monaco} from '@monaco-editor/react';

type EditorTheme = 'stock' | 'ds';
type ColorMode = 'light' | 'dark';

const DS_THEME_NAMES = {light: 'c4-light', dark: 'c4-dark'} as const satisfies Record<ColorMode, string>;

const WORKBENCH_COLORS = {
	'editor.background': '--input-background',
	'editorGutter.background': '--input-background',
	'editor.foreground': '--foreground',
	'editorCursor.foreground': '--foreground',
	'editor.lineHighlightBackground': '--neutral-background-medium',
	'editor.selectionBackground': '--accent-background-strong',
	'editor.inactiveSelectionBackground': '--accent-background-subtle',
	'editor.selectionHighlightBackground': '--accent-background-subtle',
	'editor.findMatchBackground': '--warning-background-strong',
	'editor.findMatchHighlightBackground': '--warning-background-subtle',
	'editorBracketMatch.background': '--accent-background-subtle',
	'editorLineNumber.foreground': '--neutral-foreground-subtle',
	'editorLineNumber.activeForeground': '--foreground',
	'editorIndentGuide.background1': '--neutral-border-subtle',
	'editorIndentGuide.activeBackground1': '--neutral-border-strong',
	'editorWhitespace.foreground': '--neutral-border-strong',
	'editorError.foreground': '--danger-foreground-subtle',
	'editorWarning.foreground': '--warning-foreground-subtle',
	'editorInfo.foreground': '--info-foreground-subtle',
	'editorWidget.background': '--popover',
	'editorWidget.foreground': '--popover-foreground',
	'editorWidget.border': '--neutral-border-subtle',
	'editorHoverWidget.background': '--popover',
	'editorHoverWidget.border': '--neutral-border-subtle',
	'editorSuggestWidget.background': '--popover',
	'editorSuggestWidget.foreground': '--popover-foreground',
	'editorSuggestWidget.border': '--neutral-border-subtle',
	'editorSuggestWidget.selectedBackground': '--accent-background-subtle',
	'editorSuggestWidget.highlightForeground': '--accent-foreground-strong',
	'editorMarkerNavigation.background': '--popover',
	'editorMarkerNavigationError.background': '--danger-foreground-subtle',
	'editorMarkerNavigationWarning.background': '--warning-foreground-subtle',
	'editorMarkerNavigationInfo.background': '--info-foreground-subtle',
	'input.background': '--input-background',
	'input.foreground': '--foreground',
	'input.border': '--neutral-border-strong',
	'scrollbarSlider.background': '--neutral-border-subtle',
	'scrollbarSlider.hoverBackground': '--neutral-border-strong',
	'scrollbarSlider.activeBackground': '--neutral-foreground-subtle',
	'diffEditor.border': '--neutral-border-subtle',
} as const;

const DIFF_FILLS = {
	'diffEditor.insertedTextBackground': ['--success-background-strong', 'text'],
	'diffEditor.insertedLineBackground': ['--success-background-subtle', 'line'],
	'diffEditor.removedTextBackground': ['--danger-background-strong', 'text'],
	'diffEditor.removedLineBackground': ['--danger-background-subtle', 'line'],
} as const;

const DIFF_OPACITY = {
	light: {line: 1, text: 1},
	dark: {line: 0.4, text: 0.6},
} as const;

const TOKEN_COLORS = {
	'string.key.json': '--accent-foreground-subtle',
	'string.value.json': '--success-foreground-subtle',
	number: '--warning-foreground-subtle',
	'keyword.json': '--info-foreground-subtle',
	'delimiter.bracket.json': '--neutral-foreground-subtle',
	'delimiter.array.json': '--neutral-foreground-subtle',
	'delimiter.colon.json': '--neutral-foreground-subtle',
	'delimiter.comma.json': '--neutral-foreground-subtle',
} as const;

let colorContext: CanvasRenderingContext2D | null = null;

function toHex(cssColor: string, opacity = 1) {
	colorContext ??= document.createElement('canvas').getContext('2d', {willReadFrequently: true});
	if (colorContext === null || !CSS.supports('color', cssColor)) {
		return undefined;
	}
	colorContext.clearRect(0, 0, 1, 1);
	colorContext.fillStyle = '#000000';
	colorContext.fillStyle = cssColor;
	colorContext.fillRect(0, 0, 1, 1);
	const [red = 0, green = 0, blue = 0, alpha = 255] = colorContext.getImageData(0, 0, 1, 1).data;
	return `#${[red, green, blue, Math.round(alpha * opacity)].map((channel) => channel.toString(16).padStart(2, '0')).join('')}`;
}

function defineDsTheme(monaco: Monaco, container: HTMLElement, mode: ColorMode) {
	const styles = getComputedStyle(container);
	const resolve = (token: string, opacity?: number) => toHex(styles.getPropertyValue(token).trim(), opacity);
	const colors: Record<string, string> = {};
	for (const [key, token] of Object.entries(WORKBENCH_COLORS)) {
		const color = resolve(token);
		if (color !== undefined) {
			colors[key] = color;
		}
	}
	for (const [key, [token, kind]] of Object.entries(DIFF_FILLS)) {
		const color = resolve(token, DIFF_OPACITY[mode][kind]);
		if (color !== undefined) {
			colors[key] = color;
		}
	}
	const rules = Object.entries(TOKEN_COLORS).flatMap(([name, token]) => {
		const color = resolve(token);
		return color === undefined ? [] : [{token: name, foreground: color.slice(1, 7)}];
	});

	monaco.editor.defineTheme(DS_THEME_NAMES[mode], {
		base: mode === 'dark' ? 'vs-dark' : 'vs',
		inherit: true,
		rules,
		colors,
	});
}

function applyDsTheme(monaco: Monaco, container: HTMLElement, mode: ColorMode) {
	defineDsTheme(monaco, container, mode);
	monaco.editor.setTheme(DS_THEME_NAMES[mode]);
}

const STOCK_THEME_NAMES = {light: 'light', dark: 'vs-dark'} as const satisfies Record<ColorMode, string>;

function getMonacoThemeName(theme: EditorTheme, mode: ColorMode) {
	return (theme === 'ds' ? DS_THEME_NAMES : STOCK_THEME_NAMES)[mode];
}

export {getMonacoThemeName, defineDsTheme, applyDsTheme};
export type {EditorTheme};
