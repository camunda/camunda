/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useRef, useState} from 'react';
import {Button, Modal} from '@carbon/react';
import {Edit, View} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {RichTextEditor, type EditorHandle} from './RichTextEditor';
import {Toolbar} from './editorStyles';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {isValidJSON} from '#/shared/json/isValidJSON';

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
	const transformCopyValue = useCallback(
		(valueToCopy: string) =>
			language === 'json' && variableName !== undefined && isValidJSON(valueToCopy)
				? JSON.stringify({[variableName]: JSON.parse(valueToCopy)})
				: valueToCopy,
		[language, variableName],
	);

	return (
		<Modal
			open
			modalHeading={isInEditMode && editModeTitle ? editModeTitle : title}
			modalAriaLabel={isInEditMode && editModeTitle ? editModeTitle : (title ?? t('operate.shared.editors.value'))}
			onRequestClose={onClose}
			onRequestSubmit={() => {
				if (language === 'json' && !isValidJSON(editedValue)) {
					setHasValidationError(true);
					editorRef.current?.showMarkers();
					return;
				}
				onApply?.(language === 'json' ? JSON.stringify(JSON.parse(editedValue)) : editedValue);
			}}
			size="lg"
			primaryButtonText={t('operate.shared.editors.apply')}
			secondaryButtonText={t('operate.shared.editors.cancel')}
			closeButtonLabel={t('operate.shared.editors.close')}
			passiveModal={isReadOnly}
			preventCloseOnClickOutside
		>
			<Toolbar>
				{readOnly && allowModeToggle && (
					<Button
						kind="ghost"
						size="sm"
						renderIcon={isInEditMode ? View : Edit}
						onClick={() => {
							setIsInEditMode(!isInEditMode);
							if (isInEditMode) {
								setEditedValue(initialValue);
							}
							setHasValidationError(false);
						}}
					>
						{isInEditMode ? t('operate.shared.editors.view') : t('operate.shared.editors.edit')}
					</Button>
				)}
				<CopyButton value={editedValue} transformValue={transformCopyValue} />
			</Toolbar>
			<RichTextEditor
				value={editedValue}
				language={language}
				readOnly={isReadOnly}
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
			{hasValidationError && <div role="alert">{t('operate.shared.editors.invalidJSON')}</div>}
		</Modal>
	);
};

const RichTextEditorModal = (props: Props) =>
	props.isVisible ? <ModalContent key={`${props.language}:${props.readOnly}:${props.value}`} {...props} /> : null;

export {RichTextEditorModal};
