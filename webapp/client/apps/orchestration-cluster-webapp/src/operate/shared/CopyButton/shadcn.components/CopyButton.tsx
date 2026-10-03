/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, IconButton} from '@camunda/design-system';
import {Check, Copy} from '@camunda/design-system/icons';
import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';

type TooltipSide = React.ComponentProps<typeof IconButton>['tooltipSide'];

type Props = {
	value: string;
	transformValue?: (value: string) => string;
	hasIconOnly?: boolean;
	tooltipAlignment?: string;
};

const COPY_FEEDBACK_TIMEOUT_MS = 5000;

function getTooltipSide(tooltipAlignment?: string): TooltipSide {
	if (tooltipAlignment?.includes('top')) {
		return 'top';
	}

	if (tooltipAlignment?.includes('bottom')) {
		return 'bottom';
	}

	if (tooltipAlignment?.includes('left')) {
		return 'left';
	}

	if (tooltipAlignment?.includes('right')) {
		return 'right';
	}

	return undefined;
}

const CopyButton: React.FC<Props> = ({value, transformValue, hasIconOnly, tooltipAlignment}) => {
	const {t} = useTranslation();
	const [isCopied, setIsCopied] = useState(false);
	const copyTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const latestValueRef = useRef(value);

	function resetTimeout() {
		if (copyTimeoutRef.current !== null) {
			clearTimeout(copyTimeoutRef.current);
			copyTimeoutRef.current = null;
		}
	}

	useEffect(() => {
		return () => {
			resetTimeout();
		};
	}, []);

	useEffect(() => {
		latestValueRef.current = value;
		// eslint-disable-next-line react-hooks/set-state-in-effect
		setIsCopied(false);
		resetTimeout();
	}, [value]);

	const handleCopy = useCallback(() => {
		if (navigator.clipboard === undefined) {
			return;
		}

		const sourceValue = value;
		const copiedValue = transformValue?.(sourceValue) ?? sourceValue;

		navigator.clipboard
			.writeText(copiedValue)
			.then(() => {
				if (latestValueRef.current !== sourceValue) {
					return;
				}

				setIsCopied(true);
				if (copyTimeoutRef.current !== null) {
					clearTimeout(copyTimeoutRef.current);
				}
				copyTimeoutRef.current = setTimeout(() => {
					setIsCopied(false);
				}, COPY_FEEDBACK_TIMEOUT_MS);
			})
			.catch(() => {
				// Clipboard write blocked (insecure context, missing permission), silently ignore
			});
	}, [transformValue, value]);

	const label = isCopied ? t('operate.shared.copyButton.copied') : t('operate.shared.copyButton.copy');
	const tooltipLabel = isCopied
		? t('operate.shared.copyButton.copiedToClipboard')
		: t('operate.shared.copyButton.copyToClipboard');
	const Icon = isCopied ? Check : Copy;

	if (hasIconOnly) {
		return (
			<IconButton
				variant="ghost"
				size="sm"
				label={tooltipLabel}
				icon={Icon}
				tooltipSide={getTooltipSide(tooltipAlignment)}
				onClick={handleCopy}
			/>
		);
	}

	return (
		<Button variant="ghost" size="sm" onClick={handleCopy}>
			<Icon className="size-4" aria-hidden="true" />
			{label}
		</Button>
	);
};

export {CopyButton};
