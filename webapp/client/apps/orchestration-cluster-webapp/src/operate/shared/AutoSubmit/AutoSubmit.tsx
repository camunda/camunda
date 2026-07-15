/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useForm, useFormState} from 'react-final-form';

// ponytail: trailing-only throttle, replaces lodash/throttle(fn, wait, {leading: false}) — lodash is not a dependency of this app
function throttleTrailing(fn: () => void, wait: number) {
	let timeoutId: ReturnType<typeof setTimeout> | undefined;
	function throttled() {
		if (timeoutId === undefined) {
			timeoutId = setTimeout(() => {
				timeoutId = undefined;
				fn();
			}, wait);
		}
	}
	throttled.cancel = () => {
		if (timeoutId !== undefined) {
			clearTimeout(timeoutId);
			timeoutId = undefined;
		}
	};
	return throttled;
}

type Props = {
	fieldsToSkipTimeout?: string[];
};

const AutoSubmit: React.FC<Props> = ({fieldsToSkipTimeout = []}) => {
	const form = useForm();
	const throttledSubmit = useRef(throttleTrailing(form.submit, 100));

	const {dirtyFields, values} = useFormState({
		subscription: {
			dirtyFields: true,
			values: true,
		},
	});
	const shouldSkipTimeout = fieldsToSkipTimeout.map((field) => dirtyFields?.[field]).some((field) => field);

	const isDirty = Object.entries(dirtyFields || {}).filter(([, value]) => value).length > 0;

	useEffect(() => {
		if (isDirty && shouldSkipTimeout) {
			const throttled = throttledSubmit.current;
			throttled();

			return () => throttled.cancel();
		}

		const timeoutId = isDirty ? setTimeout(form.submit, 750) : undefined;

		return () => {
			if (timeoutId !== undefined) {
				clearTimeout(timeoutId);
			}
		};
	}, [shouldSkipTimeout, isDirty, values, form]);

	return null;
};

export {AutoSubmit};
