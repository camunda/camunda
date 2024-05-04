/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {TextInput} from '@carbon/react';
import {useFieldError} from 'modules/hooks/useFieldError';

type Props = React.ComponentProps<typeof TextInput> & {
  name: string;
};

const TextInputField = React.forwardRef<HTMLInputElement, Props>(
  ({name, ...props}, ref) => {
    const error = useFieldError(name);

    return (
      <TextInput
        ref={ref}
        {...props}
        invalid={error !== undefined}
        invalidText={error}
      />
    );
  },
);

export {TextInputField};
