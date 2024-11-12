/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useForm, useFormState} from 'react-final-form';
import throttle from 'lodash/throttle';

type Props = {
  fieldsToSkipTimeout?: string[];
};

const AutoSubmit: React.FC<Props> = ({fieldsToSkipTimeout = []}) => {
  const form = useForm();
  const throttledSubmit = useRef(throttle(form.submit, 100, {leading: false}));

  const {dirtyFields, values} = useFormState({
    subscription: {
      dirtyFields: true,
      values: true,
    },
  });
  const shouldSkipTimeout = fieldsToSkipTimeout
    .map((field) => dirtyFields[field])
    .some((field) => field);

  const isDirty =
    Object.entries(dirtyFields).filter(([, value]) => value).length > 0;

  useEffect(() => {
    if (isDirty && shouldSkipTimeout) {
      throttledSubmit.current();

      return;
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
