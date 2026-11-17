/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useRef, useState} from 'react';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from '@camunda/design-system';
import {Pencil, Eye} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {CopyButton} from '#/operate/shared/CopyButton/shadcn.components/CopyButton';
import type {EditorHandle} from '#/operate/shared/Editors/MonacoEditor';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {isValidJSON} from '#/shared/json/isValidJSON';
import {RichTextEditor} from './RichTextEditor';

type Props = {
	language?: 'json' | 'markdown';
	value: string;
	isVisible: boolean;
	onClose?: () => void;
	onApply?: (value: string) => void;
	title?: string;
	editModeTitle?: string;
	readOnly?: boolean;
	allowModeToggle?: boolean;
	variableName?: string;
};

const ModalContent = ({
	language = 'json',
	value,
	onClose,
	onApply,
	title,
	editModeTitle,
	readOnly = false,
	allowModeToggle = false,
	variableName,
}: Props) => {
	const {t} = useTranslation();
	const initialValue = useMemo(() => (language === 'json' ? beautifyJSON(value) : value), [language, value]);
	const [editedValue, setEditedValue] = useState(initialValue);
	const [isInEditMode, setIsInEditMode] = useState(!readOnly);
	const [hasValidationError, setHasValidationError] = useState(false);
	const editorRef = useRef<EditorHandle | null>(null);
	const isReadOnly = readOnly && !isInEditMode;
	const heading = (isInEditMode && editModeTitle ? editModeTitle : title) ?? t('operate.shared.editors.value');
	const transformCopyValue = useCallback(
		(valueToCopy: string) =>
			language === 'json' && variableName !== undefined && isValidJSON(valueToCopy)
				? JSON.stringify({[variableName]: JSON.parse(valueToCopy)})
				: valueToCopy,
		[language, variableName],
	);
	const ModeIcon = isInEditMode ? Eye : Pencil;

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose?.();
				}
			}}
		>
			<DialogContent
				size="lg"
				aria-describedby={undefined}
				closeLabel={t('operate.shared.editors.close')}
				dismissOnOutsideClick={false}
			>
				<DialogHeader>
					<DialogTitle>{heading}</DialogTitle>
				</DialogHeader>
				<DialogBody className="space-y-2">
					<div className="flex justify-end gap-2">
						{readOnly && allowModeToggle && (
							<Button
								variant="ghost"
								size="sm"
								onClick={() => {
									setIsInEditMode(!isInEditMode);
									if (isInEditMode) {
										setEditedValue(initialValue);
									}
									setHasValidationError(false);
								}}
							>
								<ModeIcon className="size-4" aria-hidden="true" />
								{isInEditMode ? t('operate.shared.editors.view') : t('operate.shared.editors.edit')}
							</Button>
						)}
						<CopyButton value={editedValue} transformValue={transformCopyValue} />
					</div>
					<RichTextEditor
						value={editedValue}
						language={language}
						readOnly={isReadOnly}
						isInvalid={hasValidationError}
						onChange={(nextValue) => {
							setEditedValue(nextValue);
							setHasValidationError(false);
						}}
						onValidate={(isValid) => {
							if (isValid) {
								editorRef.current?.hideMarkers();
							}
						}}
						onMount={(editor) => {
							editorRef.current = editor;
						}}
					/>
					{hasValidationError && (
						<p role="alert" className="text-sm text-danger-foreground-subtle">
							{t('operate.shared.editors.invalidJSON')}
						</p>
					)}
				</DialogBody>
				{!isReadOnly && (
					<DialogFooter>
						<Button variant="secondary" onClick={onClose}>
							{t('operate.shared.editors.cancel')}
						</Button>
						<Button
							onClick={() => {
								if (language === 'json' && !isValidJSON(editedValue)) {
									setHasValidationError(true);
									editorRef.current?.showMarkers();
									return;
								}
								onApply?.(language === 'json' ? JSON.stringify(JSON.parse(editedValue)) : editedValue);
							}}
						>
							{t('operate.shared.editors.apply')}
						</Button>
					</DialogFooter>
				)}
			</DialogContent>
		</Dialog>
	);
};

const RichTextEditorModal = (props: Props) =>
	props.isVisible ? <ModalContent key={`${props.language}:${props.readOnly}:${props.value}`} {...props} /> : null;

export {RichTextEditorModal};
