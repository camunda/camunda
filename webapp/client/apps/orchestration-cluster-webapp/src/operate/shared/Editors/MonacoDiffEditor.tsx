/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {DiffEditor, type Monaco} from '@monaco-editor/react';
import type {editor} from 'monaco-editor';
import {observer} from 'mobx-react-lite';
import {useResolvedTheme} from '@camunda/design-system';
import {themeStore} from '#/shared/theme/theme';
import {applyDsTheme, defineDsTheme, getMonacoThemeName, type EditorTheme} from './dsMonacoTheme';

type Props = {
	originalValue: string;
	modifiedValue: string;
	height?: string;
	width?: string;
	theme?: EditorTheme;
};

const MonacoDiffEditor = observer(
	({originalValue, modifiedValue, height = '90vh', width = '100%', theme = 'stock'}: Props) => {
		const dsColorMode = useResolvedTheme(themeStore.selectedTheme);
		const colorMode = theme === 'ds' ? dsColorMode : themeStore.actualTheme;
		const mounted = useRef<{diffEditor: editor.IStandaloneDiffEditor; model: editor.IDiffEditorModel} | null>(null);
		const [monaco, setMonaco] = useState<Monaco | null>(null);
		const wrapperRef = useRef<HTMLElement>(null);

		useEffect(
			() => () => {
				if (mounted.current === null) {
					return;
				}
				const {diffEditor, model} = mounted.current;
				if (diffEditor.getModel() !== null) {
					diffEditor.setModel(null);
				}
				model.original.dispose();
				model.modified.dispose();
			},
			[],
		);
		useEffect(() => {
			const container = mounted.current?.diffEditor.getContainerDomNode();
			if (theme === 'ds' && monaco && container) {
				applyDsTheme(monaco, container, colorMode);
			}
		}, [theme, monaco, colorMode]);

		return (
			<DiffEditor
				height={height}
				width={width}
				original={originalValue}
				modified={modifiedValue}
				theme={getMonacoThemeName(theme, colorMode)}
				wrapperProps={{ref: wrapperRef}}
				beforeMount={(monaco) => {
					if (theme === 'ds') {
						defineDsTheme(monaco, wrapperRef.current ?? document.body, colorMode);
					}
				}}
				language="json"
				options={{
					minimap: {enabled: false},
					fontSize: 13,
					lineHeight: 20,
					fontFamily: '"IBM Plex Mono", "Droid Sans Mono", monospace',
					wordWrap: 'on',
					scrollBeyondLastLine: false,
					disableMonospaceOptimizations: true,
					readOnly: true,
					renderOverviewRuler: false,
				}}
				keepCurrentOriginalModel
				keepCurrentModifiedModel
				onMount={(diffEditor, monaco) => {
					const model = diffEditor.getModel();
					mounted.current = model === null ? null : {diffEditor, model};
					setMonaco(monaco);
				}}
			/>
		);
	},
);

export {MonacoDiffEditor};
