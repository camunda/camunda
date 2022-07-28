/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useImperativeHandle, useState} from 'react';
import {CmTextfield} from '@camunda-cloud/common-ui-react';

type Props = {
  validation: React.ComponentProps<typeof CmTextfield>['validation'];
  validationStyle: React.ComponentProps<typeof CmTextfield>['validationStyle'];
  onCmInput: React.ChangeEventHandler<HTMLInputElement>;
  fieldSuffix: React.ComponentProps<typeof CmTextfield>['fieldSuffix'];
  shouldDebounceError: boolean;
  label?: string;
  children: React.ReactNode;
  readonly: boolean;
};

const Textfield = React.forwardRef<
  {renderValidity: () => Promise<void>},
  Props
>(
  (
    {
      children,
      validation,
      fieldSuffix,
      validationStyle,
      onCmInput,
      shouldDebounceError,
      label,
      readonly = false,
      ...props
    },
    ref
  ) => {
    const [errorMessage, setErrorMessage] = useState<string | undefined>(
      undefined
    );

    const renderValidity = async () => {
      if (validation?.type === 'custom') {
        const result = await validation.validator('');
        return result.isValid
          ? setErrorMessage(undefined)
          : setErrorMessage(result.message);
      }
    };

    useImperativeHandle(ref, () => ({
      renderValidity,
      forceFocus: () => {},
    }));

    return (
      <>
        <label>
          {label}
          <input {...props} onChange={onCmInput} readOnly={readonly} />
        </label>
        <div>{errorMessage}</div>
        {fieldSuffix?.type === 'icon' && (
          <div title="open json editor modal" onClick={fieldSuffix.press}></div>
        )}
      </>
    );
  }
);

export {Textfield};
