/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TextInput} from '@carbon/react';

type Props = {
  type: 'from' | 'to';
  labelText: string;
  value: string;
  onChange: (value: string) => void;
  onChangeCallback?: () => void;
  invalid?: boolean;
  invalidText?: string;
};

const TimeInput: React.FC<Props> = ({
  type,
  labelText,
  value,
  onChange,
  onChangeCallback,
  invalid,
  invalidText,
}) => {
  return (
    <TextInput
      value={value}
      id={`time-picker-${type}`}
      labelText={labelText}
      size="sm"
      onChange={(event) => {
        onChange(event.target.value);
        onChangeCallback?.();
      }}
      placeholder="hh:mm:ss"
      data-testid={`${type}Time`}
      maxLength={8}
      autoComplete="off"
      invalid={invalid}
      invalidText={invalidText}
    />
  );
};

export {TimeInput};

