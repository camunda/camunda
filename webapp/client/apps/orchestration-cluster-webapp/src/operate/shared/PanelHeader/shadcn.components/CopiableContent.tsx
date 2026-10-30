/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, InlineCode} from '@camunda/design-system';
import {Check, Copy} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {notificationsStore} from '#/shared/notifications/notifications.store';

type Props = {
	content: string;
	copyButtonDescription: string;
	title?: string;
	className?: string;
};

const COPY_FEEDBACK_TIMEOUT_MS = 2000;

// navigator.clipboard is undefined outside secure contexts (e.g. self-managed Operate served over plain HTTP)
async function copyToClipboard(text: string) {
	if (navigator.clipboard !== undefined) {
		await navigator.clipboard.writeText(text);
		return;
	}

	const textarea = document.createElement('textarea');
	textarea.value = text;
	textarea.setAttribute('readonly', '');
	textarea.style.position = 'fixed';
	textarea.style.opacity = '0';
	document.body.appendChild(textarea);
	try {
		textarea.select();
		if (!document.execCommand('copy')) {
			throw new Error('Copy command was rejected');
		}
	} finally {
		document.body.removeChild(textarea);
	}
}

const CopiableContent: React.FC<Props> = ({content, copyButtonDescription, title, className}) => {
	const {t} = useTranslation();
	const [copiedContent, setCopiedContent] = useState<string | null>(null);
	const isCopied = copiedContent === content;
	const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const generationRef = useRef(0);

	useEffect(() => {
		return () => {
			generationRef.current += 1;
			if (timeoutRef.current !== null) {
				clearTimeout(timeoutRef.current);
			}
			setCopiedContent(null);
		};
	}, [content]);

	function handleCopy() {
		const generation = generationRef.current;
		copyToClipboard(content)
			.then(() => {
				if (generationRef.current !== generation) {
					return;
				}
				setCopiedContent(content);
				if (timeoutRef.current !== null) {
					clearTimeout(timeoutRef.current);
				}
				timeoutRef.current = setTimeout(() => setCopiedContent(null), COPY_FEEDBACK_TIMEOUT_MS);
			})
			.catch(() => {
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('operate.shared.copyButton.copyFailed'),
					isDismissable: true,
				});
			});
	}

	return (
		<span className={cn('inline-flex items-center', className)}>
			<Button
				type="button"
				variant="ghost"
				size="sm"
				title={title ?? copyButtonDescription}
				aria-label={`${copyButtonDescription}: ${content}`}
				onClick={handleCopy}
			>
				<InlineCode>{content}</InlineCode>
				{isCopied ? <Check aria-hidden /> : <Copy aria-hidden />}
			</Button>
			<span role="status" className="sr-only">
				{isCopied ? t('operate.shared.copyButton.copiedToClipboard') : ''}
			</span>
		</span>
	);
};

export {CopiableContent};
