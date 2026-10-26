/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {DiffEditor} from '@monaco-editor/react';
import type {editor} from 'monaco-editor';
import {observer} from 'mobx-react-lite';
import {themeStore} from '#/shared/theme/theme';

type Props = {
	originalValue: string;
	modifiedValue: string;
	height?: string;
	width?: string;
};

const MonacoDiffEditor = observer(({originalValue, modifiedValue, height = '90vh', width = '100%'}: Props) => {
	const mounted = useRef<{diffEditor: editor.IStandaloneDiffEditor; model: editor.IDiffEditorModel} | null>(null);

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

	return (
		<DiffEditor
			height={height}
			width={width}
			original={originalValue}
			modified={modifiedValue}
			theme={themeStore.actualTheme === 'dark' ? 'vs-dark' : 'light'}
			language="json"
			options={{
				minimap: {enabled: false},
				fontSize: 13,
				lineHeight: 20,
				fontFamily: '"IBM Plex Mono", "Droid Sans Mono", monospace',
				wordWrap: 'on',
				scrollBeyondLastLine: false,
				readOnly: true,
				renderOverviewRuler: false,
			}}
			keepCurrentOriginalModel
			keepCurrentModifiedModel
			onMount={(diffEditor) => {
				const model = diffEditor.getModel();
				mounted.current = model === null ? null : {diffEditor, model};
			}}
		/>
	);
});

export {MonacoDiffEditor};
