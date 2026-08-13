/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import {ComposedModal, ModalBody, ModalFooter, ModalHeader} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import Editor from '@monaco-editor/react';
import type {editor} from 'monaco-editor';
import {useTranslation} from 'react-i18next';
import {themeStore} from '#/shared/theme/theme';
import {isValidJSON} from '#/tasklist/modules/json/isValidJSON';
import styles from './JSONEditorModal.module.scss';

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

	// The compat Modal drops both `preventCloseOnClickOutside` and `size` (confirmed
	// via its own warnDroppedProps). Both matter here and are compensated DS-side:
	//
	//  - Without the outside-click guard, clicking the backdrop closes the dialog and
	//    silently discards unsaved JSON. The compat adapter spreads unknown props onto
	//    the DS DialogContent, so Radix's `onInteractOutside` reaches it and can veto
	//    the dismissal. The header's X and Esc still close, because those go through
	//    onOpenChange rather than an outside interaction.
	//  - Without `size="lg"` the dialog falls back to the DS default width, which is
	//    far too narrow for a code editor; `styles.modal` restores it.
	//
	// Applied only on the DS path so the Carbon path keeps its own props untouched and
	// never receives a Radix-only handler. Both gaps are logged for the DS team in
	// docs/migration/human-follow-up.md.
	const dsModalProps = featureFlags.dsTasklistUI
		? ({
				onInteractOutside: (event: Event) => {
					event.preventDefault();
				},
				className: styles.modal,
			} as Partial<React.ComponentProps<typeof ComposedModal>>)
		: {};

	return (
		<ComposedModal
			open={isOpen}
			preventCloseOnClickOutside
			size="lg"
			onClose={onClose}
			aria-label={title}
			{...dsModalProps}
		>
			<ModalHeader title={title} iconDescription={t('tasklist.jsonEditorCloseButtonLabel')} />
			<ModalBody>
				{isOpen ? (
					<Editor
						className={styles.editor}
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
					<div className={styles.editor} />
				)}
			</ModalBody>
			{isReadOnly ? null : (
				<ModalFooter
					primaryButtonText={isReadOnly ? undefined : t('tasklist.jsonEditorApplyButtonLabel')}
					primaryButtonDisabled={!isValid}
					secondaryButtonText={
						isReadOnly ? t('tasklist.jsonEditorCloseButtonLabel') : t('tasklist.jsonEditorCancelButtonLabel')
					}
					onRequestClose={onClose}
					onRequestSubmit={
						isReadOnly
							? undefined
							: () => {
									if (isValid) {
										onSave(editedValue);
									} else {
										editorRef.current?.getAction('editor.action.marker.next')?.run();
									}
								}
					}
				>
					{null}
				</ModalFooter>
			)}
		</ComposedModal>
	);
};

export {JSONEditorModal};
