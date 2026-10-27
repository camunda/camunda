/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useField, useFormState} from 'react-final-form';
import {getIn} from 'final-form';

const useFieldError = (name: string) => {
	const {
		input: {value},
		meta: {active, dirtySinceLastSubmit, validating},
	} = useField(name);

	const {errors, submitErrors, validating: formValidating} = useFormState();

	const error: string | undefined = getIn(errors ?? {}, name);
	const submitError: string | undefined = dirtySinceLastSubmit ? undefined : getIn(submitErrors ?? {}, name);

	const [computedError, setComputedError] = useState<string | undefined>();

	useEffect(() => {
		if ((formValidating || validating) && !active) {
			return;
		}

		// eslint-disable-next-line react-hooks/set-state-in-effect
		setComputedError(error ?? submitError);
	}, [formValidating, validating, active, error, submitError, setComputedError, value]);

	return computedError;
};

export {useFieldError};
