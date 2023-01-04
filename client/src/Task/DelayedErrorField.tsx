/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldValidator} from 'final-form';
import {useEffect, useState} from 'react';
import {
  Field,
  useField,
  FieldInputProps,
  FieldMetaState,
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
