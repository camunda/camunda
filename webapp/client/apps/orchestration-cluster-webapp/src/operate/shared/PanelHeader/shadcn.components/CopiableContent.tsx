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

type Props = {
	content: string;
	copyButtonDescription: string;
	className?: string;
};

const COPY_FEEDBACK_TIMEOUT_MS = 2000;

const CopiableContent: React.FC<Props> = ({content, copyButtonDescription, className}) => {
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
		navigator.clipboard
			?.writeText(content)
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
			.catch(() => {});
	}

	return (
		<span className={cn('inline-flex items-center', className)}>
			<Button
				type="button"
				variant="ghost"
				size="sm"
				title={copyButtonDescription}
				aria-label={copyButtonDescription}
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
