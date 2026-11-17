/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Input as BaseTextInput, Label} from '@camunda/design-system';
import {forwardRef, useEffect, useImperativeHandle, useRef} from 'react';
import type {FieldInputProps} from 'react-final-form';

type TextInputRef = HTMLInputElement | null;

type Props = Omit<React.ComponentProps<typeof BaseTextInput>, 'onBlur' | 'onFocus'> & {
	labelText: React.ReactNode;
	onBlur?: FieldInputProps<string>['onBlur'];
	onFocus?: FieldInputProps<string>['onFocus'];
};

const TextInput = forwardRef<TextInputRef, Props>(
	({autoFocus, onFocus, onBlur, invalidText, labelText, id, ...props}, forwardedRef) => {
		const inputRef = useRef<TextInputRef>(null);

		// This effect is necessary because of this bug https://github.com/final-form/react-final-form/issues/558
		useEffect(() => {
			if (autoFocus) {
				setTimeout(() => inputRef.current?.focus(), 0);
			}
		}, [autoFocus]);

		useImperativeHandle<TextInputRef, TextInputRef>(forwardedRef, () => inputRef.current);

		return (
			<>
				<Label className="sr-only" htmlFor={id}>
					{labelText}
				</Label>
				<BaseTextInput
					{...props}
					id={id}
					onBlur={onBlur}
					onFocus={onFocus}
					aria-invalid={invalidText !== undefined}
					invalidText={invalidText}
					ref={inputRef}
				/>
			</>
		);
	},
);

export {TextInput};
