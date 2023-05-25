/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {Checkbox as CarbonCheckbox, Stack} from '@carbon/react';
import {Icon} from '@carbon/react/icons';
import {Checkbox} from 'modules/components/Carbon/Checkbox';
import {Group} from './styled';

type GroupItem = {
  label: string;
  name: string;
  Icon: Icon;
};

type Props = {
  dataTestId: string;
  groupLabel: string;
  items: GroupItem[];
};

const CheckboxGroup: React.FC<Props> = ({dataTestId, groupLabel, items}) => {
  const form = useForm();
  const fieldValues = items.map(({name}) =>
    Boolean(form.getState().values[name])
  );
  const isChecked = fieldValues.every((value) => value);
  const isIndeterminate = fieldValues.some((value) => value);

  return (
    <Stack gap={1}>
      <CarbonCheckbox
        labelText={groupLabel}
        id={groupLabel}
        data-testid={dataTestId}
        checked={isChecked ?? undefined}
        indeterminate={isIndeterminate && !isChecked}
        onChange={() => {
          form.batch(() => {
            items.forEach(({name}) => {
              form.change(name, !isChecked);
            });
          });
        }}
      />
      <Group>
        {items.map(({label, name, Icon}) => (
          <Field name={name} component="input" type="checkbox" key={name}>
            {({input}) => (
              <Checkbox
                input={input}
                labelText={label}
                data-testid={`filter-${name}`}
                Icon={Icon}
              />
            )}
          </Field>
        ))}
      </Group>
    </Stack>
  );
};

export {CheckboxGroup};
