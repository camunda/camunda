/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {useEffect} from 'react';
import {cn} from '#/shared/cn';

type Status = 'inactive' | 'active' | 'finished' | 'error';

type Props = {
	buttonProps?: React.ComponentProps<typeof Button>;
	children: React.ReactNode;
	description?: string;
	status: Status;
	isHidden?: boolean;
	ariaLive?: 'assertive' | 'polite';
	onError?: () => void;
	onSuccess?: () => void;
};

const AsyncActionButton: React.FC<Props> = ({
	buttonProps,
	children,
	description,
	status,
	isHidden,
	ariaLive = 'polite',
	onError,
	onSuccess,
}) => {
	useEffect(() => {
		if (onError === undefined || status !== 'error') {
			return;
		}

		const timeoutId = setTimeout(onError, 500);
		return () => clearTimeout(timeoutId);
	}, [onError, status]);

	useEffect(() => {
		if (onSuccess === undefined || status !== 'finished') {
			return;
		}

		const timeoutId = setTimeout(onSuccess, 500);
		return () => clearTimeout(timeoutId);
	}, [onSuccess, status]);

	return (
		<Button {...buttonProps} loading={status === 'active'} className={cn(isHidden && 'hidden', buttonProps?.className)}>
			<span aria-live={ariaLive}>{status === 'inactive' ? children : (description ?? children)}</span>
		</Button>
	);
};

export {AsyncActionButton};
