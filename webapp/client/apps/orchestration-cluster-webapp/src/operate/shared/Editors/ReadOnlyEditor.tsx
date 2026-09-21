/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState, type ReactNode} from 'react';
import {InlineLoading} from '@carbon/react';
import {Copy} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ReadOnlyWrapper, ReadOnlyContent} from './editorStyles';

type Props = {
	value: string;
	placeholder: string;
	label: string;
	height: number;
	isInvalid: boolean;
	renderButton?: () => ReactNode;
	onCopy?: () => Promise<string>;
};

const ReadOnlyEditor = ({value, placeholder, label, height, isInvalid, renderButton, onCopy}: Props) => {
	const {t} = useTranslation();
	const [isCopying, setIsCopying] = useState(false);
	const isCopyingRef = useRef(false);
	const notify = (kind: 'success' | 'error', title: string) =>
		notificationsStore.displayNotification({kind, title, isDismissable: true});

	const handleCopy = async () => {
		if (isCopyingRef.current) {
			return;
		}
		isCopyingRef.current = true;
		setIsCopying(true);
		try {
			let valueToCopy = value;
			if (onCopy) {
				try {
					valueToCopy = await onCopy();
				} catch {
					notify('error', t('operate.shared.editors.fetchFailed', {label}));
					return;
				}
			}
			try {
				await navigator.clipboard.writeText(valueToCopy);
				notify('success', t('operate.shared.editors.copied', {label}));
			} catch {
				notify('error', t('operate.shared.editors.copyFailed', {label}));
			}
		} finally {
			isCopyingRef.current = false;
			setIsCopying(false);
		}
	};

	return (
		<ReadOnlyWrapper $invalid={isInvalid} $empty={value === ''}>
			<ReadOnlyContent
				$height={height}
				tabIndex={0}
				role="button"
				aria-label={t('operate.shared.editors.copyValue', {label})}
				aria-disabled={isCopying}
				onClick={handleCopy}
				onKeyDown={(event) => {
					if (event.key === 'Enter' || event.key === ' ') {
						event.preventDefault();
						void handleCopy();
					}
				}}
			>
				{value || placeholder}
			</ReadOnlyContent>
			{renderButton?.()}
			{isCopying ? <InlineLoading description={t('operate.shared.editors.copying')} /> : <Copy size={16} aria-hidden />}
		</ReadOnlyWrapper>
	);
};

export {ReadOnlyEditor};
