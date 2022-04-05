/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';
import {useFieldError} from 'modules/hooks/useFieldError';
import {CmTextfield} from '@camunda-cloud/common-ui-react';
import {ValidatorResult} from '@camunda-cloud/common-ui/dist/types/globalHelpers';

type Props = Omit<
  React.ComponentProps<typeof CmTextfield>,
  'formName' | 'onCmInput' | 'validationStyle' | 'validation'
> & {
  name: string;
  onChange: React.ComponentProps<typeof CmTextfield>['onCmInput'];
  shouldDebounceError: boolean;
  autoFocus?: boolean;
};

const TextField: React.FC<Props> = ({
  name,
  onChange,
  shouldDebounceError,
  autoFocus,
  ...props
}) => {
  const fieldRef = useRef<HTMLCmTextfieldElement | null>(null);
  const error = useFieldError(name);
  const timeoutId = useRef<NodeJS.Timeout | null>(null);

  const toCommonUiError = (error: string | undefined): ValidatorResult => {
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
  };

  const runAfterDelay = (validator: () => void) => {
    if (timeoutId.current !== null) {
      clearTimeout(timeoutId.current);
      timeoutId.current = null;
    }
    timeoutId.current = setTimeout(() => {
      validator();
    }, 500);
  };

  useEffect(() => {
    if (autoFocus) {
      fieldRef.current?.forceFocus();
    }
  }, [autoFocus]);

  useEffect(() => {
    if (shouldDebounceError) {
      runAfterDelay(() => fieldRef.current?.renderValidity());
    } else {
      fieldRef.current?.renderValidity();
    }
  }, [error, shouldDebounceError]);

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
