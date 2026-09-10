/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef} from 'react';
import {LoaderCircle} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {TextInput} from './TextInput';

type Props = React.ComponentProps<typeof TextInput> & {
	isLoading: boolean;
	isActive?: boolean;
};

const LoadingTextarea: React.FC<Props> = ({isLoading, isActive = false, ...props}) => {
	const {t} = useTranslation();
	const inputRef = useRef<HTMLInputElement | null>(null);

	useLayoutEffect(() => {
		if (isActive && !isLoading) {
			inputRef.current?.focus();
			inputRef.current?.setSelectionRange(inputRef.current.value.length, inputRef.current.value.length);
		}
	}, [isLoading, isActive]);

	if (isLoading) {
		return (
			<div className="relative w-full" data-testid="textarea-loading-overlay" aria-busy>
				<div className="absolute inset-0 z-2 flex items-center justify-center bg-background/80">
					<LoaderCircle className="size-4 animate-spin" aria-hidden />
					<span className="sr-only" role="status">
						{t('tasklist.processesLoadingMore')}
					</span>
				</div>
				<TextInput ref={inputRef} {...props} disabled />
			</div>
		);
	}

	return <TextInput ref={inputRef} {...props} />;
};

export {LoadingTextarea};
