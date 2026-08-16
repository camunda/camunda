/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ButtonProps, type InlineLoadingProps, Button, InlineLoading} from '#/shared/design-system-compat';
import {useEffect} from 'react';
import {cn} from '#/shared/cn';
import {featureFlags} from '#/shared/feature-flags';
import styles from './AsyncActionButton.module.scss';

type Props = {
	inlineLoadingProps?: Omit<InlineLoadingProps, 'status' | 'successDelay'>;
	buttonProps?: ButtonProps<'button'>;
	children?: React.ReactNode;
	status: NonNullable<InlineLoadingProps['status']>;
	isHidden?: boolean;
	onError?: () => void;
};

const AsyncActionButton: React.FC<Props> = ({children, inlineLoadingProps, buttonProps, status, isHidden, onError}) => {
	const {onSuccess, ...restInlineLoadingProps} = inlineLoadingProps ?? {};

	useEffect(() => {
		let timeoutId: ReturnType<typeof setTimeout> | undefined;

		if (onError !== undefined && status === 'error') {
			timeoutId = setTimeout(onError, 500);
		}

		return () => {
			if (timeoutId !== undefined) {
				clearTimeout(timeoutId);
			}
		};
	}, [onError, status]);

	useEffect(() => {
		let timeoutId: ReturnType<typeof setTimeout> | undefined;

		if (onSuccess !== undefined && status === 'finished') {
			timeoutId = setTimeout(onSuccess, 500);
		}

		return () => {
			if (timeoutId !== undefined) {
				clearTimeout(timeoutId);
			}
		};
	}, [onSuccess, status]);

	if (status === 'inactive') {
		return (
			<Button {...buttonProps} className={cn(isHidden && styles.hide, buttonProps?.className, styles.button)}>
				{children}
			</Button>
		);
	}

	// DS-only: the DS Button's own `loading` prop (spinner + aria-busy +
	// click-guard, see button.tsx) instead of Carbon's InlineLoading, which
	// is a SHIM (bare Carbon passthrough per mapping.json) and still renders
	// cds--inline-loading markup even with the flag on. Carbon path below is
	// untouched. `loading` is only true for 'active' (spinner); 'finished'/
	// 'error' show the confirmation/error text on a plain (non-spinning)
	// button until the existing onSuccess/onError timeout reverts it.
	if (featureFlags.dsTasklistUI) {
		return (
			<Button
				{...buttonProps}
				loading={status === 'active'}
				className={cn(isHidden && styles.hide, buttonProps?.className, styles.button)}
			>
				<span aria-live={restInlineLoadingProps['aria-live'] ?? 'polite'}>
					{restInlineLoadingProps.description ?? children}
				</span>
			</Button>
		);
	}

	return (
		<InlineLoading
			{...restInlineLoadingProps}
			className={cn(restInlineLoadingProps.className, styles.fitContent)}
			status={status}
		/>
	);
};

export {AsyncActionButton};
