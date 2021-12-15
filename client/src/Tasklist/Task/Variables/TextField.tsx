/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect} from 'react';
import {CmTextfield} from '@camunda-cloud/common-ui-react';
import {ValidatorResult} from '@camunda-cloud/common-ui/dist/types/globalHelpers';

function toCommonUiError(error: string | undefined): ValidatorResult {
  if (error === undefined) {
    return {
      isValid: true,
    };
  }

  return {
    isValid: false,
    type: 'invalid',
    message: error,
  };
}

type Props = Omit<
  React.ComponentProps<typeof CmTextfield>,
  'formName' | 'onCmInput' | 'validationStyle' | 'validation'
> & {
  name: string;
  onChange: React.ComponentProps<typeof CmTextfield>['onCmInput'];
  error?: string;
  autoFocus?: boolean;
};

const TextField: React.FC<Props> = ({
  name,
  onChange,
  error,
  autoFocus,
  ...props
}) => {
  const fieldRef = useRef<HTMLCmTextfieldElement | null>(null);

  useEffect(() => {
    fieldRef.current?.renderValidity();
  }, [error]);

  useEffect(() => {
    if (autoFocus) {
      fieldRef.current?.forceFocus();
    }
  }, [autoFocus]);

  return (
    <CmTextfield
      {...props}
      ref={fieldRef}
      onCmInput={onChange}
      validationStyle="delay"
      validation={{
        type: 'custom',
        validator: async () => {
          return toCommonUiError(error);
        },
        ignoreDefaultValidation: true,
      }}
    />
  );
};

export {TextField};
