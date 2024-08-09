/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, ForwardedRef, forwardRef, memo} from 'react';
import {TextInput} from '@carbon/react';
import {Calendar} from '@carbon/icons-react';
import classnames from 'classnames';

import {t} from 'translation';

import './PickerDateInput.scss';

interface PickerDateInputProps extends Omit<ComponentProps<typeof TextInput>, 'onChange'> {
  reference?: ForwardedRef<HTMLInputElement>;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

export const PickerDateInput = memo(
  ({onChange, onSubmit, reference, invalid, ...props}: PickerDateInputProps) => {
    const isInvalid = !!props.value && invalid;
    return (
      <div className={classnames('PickerDateInput', {isInvalid})}>
        <TextInput
          {...props}
          invalid={isInvalid}
          invalidText={t('common.filter.dateModal.invalidDate')}
          placeholder="yyyy-mm-dd"
          onChange={({target: {value}}) => onChange(value)}
          onKeyDown={({key}) => key === 'Enter' && onSubmit()}
          ref={reference}
        />
        <Calendar className="icon" />
      </div>
    );
  }
);

export default forwardRef<HTMLInputElement, PickerDateInputProps>((props, ref) => (
  <PickerDateInput {...props} reference={ref} />
));
