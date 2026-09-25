/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor, {type Monaco} from '@monaco-editor/react';
import type {editor} from 'monaco-editor';
import {useEffect, useId, useRef, useState, type ReactNode} from 'react';
import {flushSync} from 'react-dom';
import {observer} from 'mobx-react-lite';
import {useTranslation} from 'react-i18next';
import {themeStore} from '#/shared/theme/theme';

type EditorHandle = {showMarkers: () => void; hideMarkers: () => void};
type Props = {
	id?: string;
	language?: 'json' | 'markdown';
	value: string;
	readOnly?: boolean;
	isInvalid?: boolean;
	autoFocus?: boolean;
	onChange?: (value: string) => void;
	onValidate?: (isValid: boolean) => void;
	onMount?: (editor: EditorHandle) => void;
	height?: string;
	width?: string;
	options?: editor.IStandaloneEditorConstructionOptions;
	loading?: ReactNode;
	jsonSchema?: object;
};

const MonacoEditor = observer(
	({
		id,
		language = 'json',
		value,
		readOnly = false,
		isInvalid = false,
		autoFocus = true,
		onChange,
		onValidate,
		onMount,
		height = '60vh',
		width = '100%',
		options,
		loading,
		jsonSchema,
	}: Props) => {
		const {t} = useTranslation();
		const modelId = useId();
		const modelPath = `inmemory://operate-editor/${modelId}`;
		const schemaUri = `${modelPath}.schema.json`;
		const [monaco, setMonaco] = useState<Monaco | null>(null);
		const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
		const keyListener = useRef<{dispose: () => void} | null>(null);

		useEffect(() => () => keyListener.current?.dispose(), []);
		useEffect(() => {
			const textbox = editorRef.current?.getDomNode()?.querySelector('[role="textbox"]');
			if (id === undefined) {
				textbox?.removeAttribute('id');
			} else {
				textbox?.setAttribute('id', `${id}-editor`);
			}
			textbox?.setAttribute('aria-invalid', String(isInvalid));
		}, [id, monaco, isInvalid]);
		useEffect(() => {
			if (!monaco || !jsonSchema || language !== 'json') {
				return;
			}
			const defaults = monaco.languages.json.jsonDefaults;
			defaults.setDiagnosticsOptions({
				...defaults.diagnosticsOptions,
				schemas: [
					...(defaults.diagnosticsOptions.schemas ?? []).filter(({uri}: {uri: string}) => uri !== schemaUri),
					{uri: schemaUri, fileMatch: [modelPath], schema: jsonSchema},
				],
			});
			return () =>
				defaults.setDiagnosticsOptions({
					...defaults.diagnosticsOptions,
					schemas: defaults.diagnosticsOptions.schemas?.filter(({uri}: {uri: string}) => uri !== schemaUri),
				});
		}, [monaco, jsonSchema, language, schemaUri, modelPath]);

		return (
			<Editor
				loading={loading}
				language={language}
				value={value}
				height={height}
				width={width}
				path={modelPath}
				theme={themeStore.actualTheme === 'dark' ? 'vs-dark' : 'light'}
				options={{
					minimap: {enabled: false},
					fontSize: 13,
					lineHeight: 20,
					fontFamily: '"IBM Plex Mono", "Droid Sans Mono", monospace',
					formatOnPaste: true,
					formatOnType: true,
					tabSize: 2,
					wordWrap: 'on',
					scrollBeyondLastLine: false,
					ariaLabel: t('operate.shared.editors.value'),
					...options,
					readOnly,
				}}
				onChange={(value) =>
					editorRef.current?.getRawOptions().readOnly
						? onChange?.(value ?? '')
						: flushSync(() => onChange?.(value ?? ''))
				}
				onValidate={(markers) => onValidate?.(markers.length === 0)}
				onMount={(editor: editor.IStandaloneCodeEditor, monaco) => {
					editorRef.current = editor;
					setMonaco(monaco);
					if (autoFocus) {
						editor.focus();
					}
					keyListener.current = editor.onKeyDown((event) => {
						if (event.keyCode === monaco.KeyCode.Escape && document.activeElement instanceof HTMLElement) {
							document.activeElement.blur();
						}
					});
					onMount?.({
						showMarkers: () => {
							editor.trigger('', 'editor.action.marker.next', undefined);
							editor.trigger('', 'editor.action.marker.prev', undefined);
						},
						hideMarkers: () => editor.trigger('', 'closeMarkersNavigation', undefined),
					});
				}}
			/>
		);
	},
);

export {MonacoEditor};
export type {EditorHandle};
