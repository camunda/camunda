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
	const [copiedValue, setCopiedValue] = useState<string | null>(null);
	const [previousValue, setPreviousValue] = useState(value);
	const copyTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const copyGenerationRef = useRef(0);

	function resetTimeout() {
		if (copyTimeoutRef.current !== null) {
			clearTimeout(copyTimeoutRef.current);
			copyTimeoutRef.current = null;
		}
	}

	// Reset any copied state left over from a previous value whenever `value` changes, so
	// cycling back to a value copied earlier (e.g. A -> B -> A) doesn't re-enter the "Copied"
	// state without a new copy action. Adjusting state during render (rather than in an effect)
	// avoids an extra render pass, per https://react.dev/learn/you-might-not-need-an-effect.
	// Any pending feedback timeout from the previous value is harmless: it is cleared before
	// scheduling a new one in handleCopy, or it fires and redundantly resets the already-null state.
	if (value !== previousValue) {
		setPreviousValue(value);
		setCopiedValue(null);
	}

	const isCopied = copiedValue !== null && copiedValue === value;

	// Invalidate any in-flight copy whenever `value` changes, so a clipboard write that was
	// started for an earlier presentation of this value cannot resolve into "Copied" after the
	// prop has cycled away and back (e.g. A -> B -> A while the write is still pending).
	useEffect(() => {
		copyGenerationRef.current += 1;
	}, [value]);

	useEffect(() => {
		return () => {
			// Invalidate any in-flight copy so its resolution can't call setState or schedule a
			// timeout after this component has unmounted.
			copyGenerationRef.current += 1;
			resetTimeout();
		};
	}, []);

	const handleCopy = useCallback(() => {
		if (navigator.clipboard === undefined) {
			return;
		}

		const sourceValue = value;
		const copiedText = transformValue?.(sourceValue) ?? sourceValue;
		const copyGeneration = copyGenerationRef.current;

		navigator.clipboard
			.writeText(copiedText)
			.then(() => {
				if (copyGenerationRef.current !== copyGeneration) {
					return;
				}

				setCopiedValue(sourceValue);
				resetTimeout();
				copyTimeoutRef.current = setTimeout(() => {
					setCopiedValue(null);
				}, COPY_FEEDBACK_TIMEOUT_MS);
			})
			.catch(() => {});
	}, [transformValue, value]);

	const label = isCopied ? t('operate.shared.copyButton.copied') : t('operate.shared.copyButton.copy');
	const tooltipLabel = isCopied
		? t('operate.shared.copyButton.copiedToClipboard')
		: t('operate.shared.copyButton.copyToClipboard');
	const Icon = isCopied ? Check : Copy;

	// IconButton keeps focus when the label flips to "Copied", and a focused
	// element's accessible-name change alone is not reliably announced by all
	// screen readers, so broadcast the state change via a dedicated live region.
	const liveAnnouncement = (
		<span className="sr-only" role="status" aria-live="polite">
			{isCopied ? tooltipLabel : ''}
		</span>
	);

	if (hasIconOnly) {
		return (
			<>
				<IconButton
					variant="ghost"
					size="sm"
					label={tooltipLabel}
					icon={Icon}
					tooltipSide={getTooltipSide(tooltipAlignment)}
					onClick={handleCopy}
				/>
				{liveAnnouncement}
			</>
		);
	}

	return (
		<>
			<Button variant="ghost" size="sm" onClick={handleCopy}>
				<Icon className="size-4" aria-hidden="true" />
				{label}
			</Button>
			{liveAnnouncement}
		</>
	);
};

export {CopyButton};
