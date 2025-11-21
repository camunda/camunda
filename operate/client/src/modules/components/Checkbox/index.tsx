/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type CarbonIconType} from '@carbon/react/icons';
import {type FieldInputProps} from 'react-final-form';
import {CheckBox, Stack} from './styled';

type CheckboxProps = {
  input: FieldInputProps<string, HTMLElement>;
  labelText: string;
  Icon: CarbonIconType;
  isDisabled?: boolean;
};

const Checkbox: React.FC<CheckboxProps> = ({
  input,
  labelText,
  Icon,
  isDisabled = false,
}) => {
  return (
    <CheckBox
      {...input}
      labelText={
        <Stack orientation="horizontal" gap={3}>
          <Icon size={20} />
          <div>{labelText}</div>
        </Stack>
      }
      invalidText=""
      warnText=""
      id={input.name}
      disabled={isDisabled}
    />
  );
};

export {Checkbox};
