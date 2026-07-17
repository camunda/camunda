/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Checkmark, Copy} from '@carbon/react/icons';
import {Button, type ButtonBaseProps} from '@carbon/react';

type Props = {
	value: string;
	hasIconOnly?: ButtonBaseProps['hasIconOnly'];
	tooltipAlignment?: ButtonBaseProps['tooltipAlignment'];
};

const COPY_FEEDBACK_TIMEOUT_MS = 5000;

const CopyButton: React.FC<Props> = ({value, hasIconOnly, tooltipAlignment}) => {
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

		const copiedValue = value;

		navigator.clipboard
			.writeText(copiedValue)
			.then(() => {
				if (latestValueRef.current !== copiedValue) {
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
				// Clipboard write blocked (insecure context, missing permission) — silently ignore
			});
	}, [value]);

	return (
		<Button
			kind="ghost"
			size="sm"
			renderIcon={isCopied ? Checkmark : Copy}
			iconDescription={
				isCopied ? t('operate.shared.copyButton.copiedToClipboard') : t('operate.shared.copyButton.copyToClipboard')
			}
			hasIconOnly={hasIconOnly}
			tooltipAlignment={tooltipAlignment}
			onClick={handleCopy}
		>
			{isCopied ? t('operate.shared.copyButton.copied') : t('operate.shared.copyButton.copy')}
		</Button>
	);
};

export {CopyButton};
