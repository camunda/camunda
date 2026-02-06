/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef} from 'react';
import {DatePickerInput} from '@carbon/react';

type Props = {
  type: 'from' | 'to';
  id: string;
  labelText: string;
  value: string;
  onChange: (value: string) => void;
  onChangeCallback?: () => void;
  autoFocus?: boolean;
};

const DateInput = forwardRef<HTMLDivElement, Props>(
  ({type, value, onChange, onChangeCallback, ...props}, ref) => {
    return (
      <DatePickerInput
        {...props}
        size="sm"
        onChange={(event) => {
          onChange(event.target.value);
          onChangeCallback?.();
        }}
        ref={ref}
        placeholder="YYYY-MM-DD"
        // @ts-expect-error - Carbon types are wrong
        pattern="\\d{4}-\\d{1,2}-\\d{1,2}"
        value={value}
        maxLength={10}
        autoComplete="off"
      />
    );
  },
);

DateInput.displayName = 'DateInput';

export {DateInput};

