/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ForwardedRef, forwardRef, memo} from 'react';

import {Input, Message, Icon, InputProps} from 'components';
import {t} from 'translation';

import './PickerDateInput.scss';

interface PickerDateInputProps extends Omit<InputProps, 'onChange'> {
  reference?: ForwardedRef<HTMLInputElement>;
  isInvalid?: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

export const PickerDateInput = memo(
  ({onChange, onSubmit, reference, isInvalid, ...props}: PickerDateInputProps) => {
    const invalid = !!props.value && isInvalid;
    return (
      <div className="PickerDateInput">
        <Input
          type="text"
          {...props}
          isInvalid={invalid}
          placeholder="yyyy-mm-dd"
          onChange={({target: {value}}) => onChange(value)}
          onKeyDown={({key}) => key === 'Enter' && onSubmit()}
          ref={reference}
        />
        <Icon type="calender" />
        {invalid && (
          <Message error className="PickerDateInputWarning">
            {t('common.filter.dateModal.invalidDate')}
          </Message>
        )}
      </div>
    );
  }
);

export default forwardRef<HTMLInputElement, PickerDateInputProps>((props, ref) => (
  <PickerDateInput {...props} reference={ref} />
));
