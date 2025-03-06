/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {FieldValidator} from 'final-form';
import {useEffect, useState} from 'react';
import {
  Field,
  useField,
  type FieldInputProps,
  type FieldMetaState,
} from 'react-final-form';

type Props = Omit<React.ComponentProps<typeof Field>, 'validate'> & {
  addExtraDelay: boolean;
  validate?: FieldValidator<string | undefined>;
  children: (props: {
    input: FieldInputProps<string | undefined>;
    meta: FieldMetaState<string | undefined>;
  }) => React.ReactNode;
};

const DelayedErrorField: React.FC<Props> = ({
  children,
  addExtraDelay,
  name,
  ...props
}) => {
  const {
    meta: {error},
  } = useField(name);
  const [delayedError, setDelayedError] = useState(error);

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout> | undefined;

    if (addExtraDelay) {
      timeoutId = setTimeout(() => {
        setDelayedError(error);
      }, 500);
    } else {
      setDelayedError(error);
    }

    return () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }, [error, addExtraDelay]);

  return (
    <Field name={name} {...props}>
      {({input, meta, ...otherProps}) =>
        children({input, ...otherProps, meta: {...meta, error: delayedError}})
      }
    </Field>
  );
};

export {DelayedErrorField};
