/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {RichTextEditor} from './RichTextEditor';
import {ReadOnlyEditor} from './ReadOnlyEditor';
import {EditorWrapper, WriteModeEditor} from './editorStyles';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {isValidJSON} from '#/shared/json/isValidJSON';
import {beautifyTruncatedJSON} from './beautifyTruncatedJSON';

type Props = {
	value: string;
	label?: string;
	placeholder?: string;
	isTruncatedValue?: boolean;
	autoFocus?: boolean;
	onChange?: (value: string) => void;
	onValidate?: (isValid: boolean) => void;
	onBlur?: () => void;
	onFocus?: () => void;
	readOnly?: boolean;
	isModified?: boolean;
	maxLines?: number;
	fieldError?: string;
	id?: string;
	renderButton?: () => ReactNode;
	onCopy?: () => Promise<string>;
};

const InlineJsonEditor = ({
	value,
	label,
	placeholder,
	isTruncatedValue = false,
	autoFocus = false,
	onChange,
	onValidate,
	onBlur,
	onFocus,
	readOnly = false,
	isModified = false,
	maxLines = 5,
	fieldError,
	id,
	renderButton,
	onCopy,
}: Props) => {
	const {t} = useTranslation();
	const isReadOnly = readOnly || !onChange;
	const editorLabel = label ?? t('operate.shared.editors.value');
	const displayValue = useMemo(() => {
		if (!isReadOnly && isModified) {
			return value;
		}
		return isTruncatedValue ? beautifyTruncatedJSON(value) : beautifyJSON(value);
	}, [value, isReadOnly, isModified, isTruncatedValue]);
	const height = Math.max(32, Math.min(Math.max(1, displayValue.split('\n').length), maxLines) * 20);

	return (
		<EditorWrapper
			id={id}
			role="group"
			aria-label={label}
			$invalid={!!fieldError}
			onFocus={(event) => {
				if (!(event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget))) {
					onFocus?.();
				}
			}}
			onBlur={(event) => {
				if (!(event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget))) {
					onBlur?.();
				}
			}}
		>
			{isReadOnly ? (
				<ReadOnlyEditor
					value={displayValue}
					placeholder={placeholder ?? editorLabel}
					label={label ?? t('operate.shared.editors.valueLabel')}
					height={height}
					isInvalid={!!fieldError}
					renderButton={renderButton}
					onCopy={onCopy}
				/>
			) : (
				<WriteModeEditor $invalid={!!fieldError}>
					<RichTextEditor
						id={id}
						value={displayValue}
						isInvalid={!!fieldError}
						autoFocus={autoFocus}
						height={`${height}px`}
						onChange={(nextValue) => {
							onChange?.(nextValue);
							onValidate?.(isValidJSON(nextValue));
						}}
						options={{
							ariaLabel: editorLabel,
							placeholder: placeholder ?? editorLabel,
							formatOnType: false,
							lineNumbers: 'off',
							lineDecorationsWidth: 16,
							renderLineHighlight: 'none',
							overviewRulerLanes: 0,
							stickyScroll: {enabled: false},
							glyphMargin: false,
							folding: false,
							scrollbar: {useShadows: false},
							tabFocusMode: true,
							fixedOverflowWidgets: true,
							padding: {top: 4, bottom: 4},
						}}
					/>
				</WriteModeEditor>
			)}
			{fieldError && (
				<div className="cds--form-requirement" role="alert">
					{fieldError}
				</div>
			)}
		</EditorWrapper>
	);
};

export {InlineJsonEditor};
