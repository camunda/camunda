/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {TextArea} from '@carbon/react';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';

type Props = React.ComponentProps<typeof TextArea> & {
	name: string;
};

const TextAreaField = React.forwardRef<HTMLTextAreaElement, Props>(({name, ...props}, ref) => {
	const error = useFieldError(name);

	return <TextArea ref={ref} {...props} invalid={error !== undefined} invalidText={error} />;
});

export {TextAreaField};
