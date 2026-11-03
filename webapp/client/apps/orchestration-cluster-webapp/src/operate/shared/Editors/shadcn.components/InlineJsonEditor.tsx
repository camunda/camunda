/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {isValidJSON} from '#/shared/json/isValidJSON';
import {beautifyTruncatedJSON} from '#/operate/shared/Editors/beautifyTruncatedJSON';
import {RichTextEditor} from './RichTextEditor';
import {ReadOnlyEditor} from './ReadOnlyEditor';

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
		<div
			id={id}
			role="group"
			aria-label={label}
			className="relative min-w-0"
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
				<div
					className={cn(
						'relative border-b border-input focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-ring',
						'[&_.monaco-editor_textarea:focus]:shadow-none!',
						!!fieldError &&
							'outline-2 -outline-offset-2 outline-danger-border-strong focus-within:outline-danger-border-strong',
					)}
				>
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
				</div>
			)}
			{fieldError && (
				<p role="alert" className="mt-1 text-xs text-danger-foreground-subtle">
					{fieldError}
				</p>
			)}
		</div>
	);
};

export {InlineJsonEditor};
