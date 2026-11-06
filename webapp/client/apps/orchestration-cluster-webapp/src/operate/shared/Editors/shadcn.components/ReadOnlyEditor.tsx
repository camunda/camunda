/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState, type ReactNode} from 'react';
import {Spinner} from '@camunda/design-system';
import {Copy} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {notificationsStore} from '#/shared/notifications/notifications.store';

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
		<div
			className={cn(
				'group relative min-h-8 w-full',
				value === '' && 'text-neutral-foreground-subtle',
				isInvalid && 'outline-2 -outline-offset-2 outline-danger-border-strong',
			)}
		>
			<pre
				style={{maxHeight: height}}
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
				className="m-0 cursor-pointer overflow-y-auto py-1 font-mono text-[13px]/5 wrap-anywhere whitespace-pre-wrap [tab-size:2] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring"
			>
				{value || placeholder}
			</pre>
			{renderButton?.()}
			{isCopying ? (
				<Spinner
					data-testid="copy-loading-indicator"
					size="default"
					accessibleLabel={t('operate.shared.editors.copying')}
					className="pointer-events-none absolute top-1 right-0"
				/>
			) : (
				<Copy
					aria-hidden
					className="pointer-events-none absolute top-1 right-2 size-4 opacity-0 group-focus-within:opacity-100 group-hover:opacity-100"
				/>
			)}
		</div>
	);
};

export {ReadOnlyEditor};
