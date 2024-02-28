/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Icon} from '@carbon/react/icons';
import {FieldInputProps} from 'react-final-form';
import {CheckBox, Stack} from './styled';

type CheckboxProps = {
  input: FieldInputProps<string, HTMLElement>;
  labelText: string;
  Icon: Icon;
};

const Checkbox: React.FC<CheckboxProps> = ({input, labelText, Icon}) => {
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
    />
  );
};

export {Checkbox};
