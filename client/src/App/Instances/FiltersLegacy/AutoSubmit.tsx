/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {useForm, useFormState} from 'react-final-form';

type Props = {
  fieldsToSkipTimeout?: string[];
};

const AutoSubmit: React.FC<Props> = ({fieldsToSkipTimeout = []}) => {
  const form = useForm();
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
      form.submit();

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
