/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from '@camunda/design-system';
import Editor from '@monaco-editor/react';
import type {editor} from 'monaco-editor';
import {X} from 'lucide-react';
import {useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {themeStore} from '#/shared/theme/theme';
import {isValidJSON} from '#/tasklist/modules/json/isValidJSON';

function beautifyJSON(value: string): string {
	try {
		return JSON.stringify(JSON.parse(value), null, '\t');
	} catch {
		return value;
	}
}

type Props = {
	isOpen: boolean;
	value: string;
	isReadOnly: boolean;
	onClose: () => void;
	onSave: (value: string) => void;
};

const JSONEditorModal: React.FC<Props> = ({isOpen, value, isReadOnly, onClose, onSave}) => {
	const {t} = useTranslation();
	const title = t(isReadOnly ? 'tasklist.jsonEditorViewVariableTitle' : 'tasklist.jsonEditorEditVariableTitle');
	const [editedValue, setEditedValue] = useState(() => beautifyJSON(value));
	const [isValid, setIsValid] = useState(() => isValidJSON(value));
	const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
	const options = useMemo(
		() =>
			({
				minimap: {enabled: false},
				fontSize: 13,
				lineHeight: 20,
				fontFamily: '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
				formatOnPaste: !isReadOnly,
				formatOnType: !isReadOnly,
				tabSize: 2,
				wordWrap: 'on',
				scrollBeyondLastLine: false,
				readOnly: isReadOnly,
			}) as const,
		[isReadOnly],
	);

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="full"
				showCloseButton={false}
				className="flex max-h-[90vh] w-[calc(100vw-4rem)] max-w-[60rem] flex-col"
				aria-label={title}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-2 right-2"
						aria-label={t('tasklist.jsonEditorCloseButtonLabel')}
						onClick={onClose}
					>
						<X aria-hidden />
					</Button>
				</DialogHeader>
				<DialogBody className="min-h-0 flex-1">
					{isOpen ? (
						<Editor
							className="h-[60vh]"
							language="json"
							value={editedValue}
							options={options}
							onChange={
								isReadOnly
									? undefined
									: (newValue) => {
											const nextValue = newValue ?? '';
											setEditedValue(nextValue);
											setIsValid(isValidJSON(nextValue));
										}
							}
							onMount={(editorInstance, monaco) => {
								editorRef.current = editorInstance;
								monaco.editor.setTheme(themeStore.actualTheme === 'light' ? 'light' : 'vs-dark');
								editorInstance.focus();
							}}
						/>
					) : (
						<div className="h-[60vh]" />
					)}
				</DialogBody>
				{isReadOnly ? null : (
					<DialogFooter>
						<Button type="button" variant="secondary" onClick={onClose}>
							{t('tasklist.jsonEditorCancelButtonLabel')}
						</Button>
						<Button
							type="button"
							disabled={!isValid}
							onClick={() => {
								if (isValid) {
									onSave(editedValue);
								} else {
									void editorRef.current?.getAction('editor.action.marker.next')?.run();
								}
							}}
						>
							{t('tasklist.jsonEditorApplyButtonLabel')}
						</Button>
					</DialogFooter>
				)}
			</DialogContent>
		</Dialog>
	);
};

export {JSONEditorModal};
