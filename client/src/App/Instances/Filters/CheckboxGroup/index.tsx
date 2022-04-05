/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';

import {Container, Group, GroupCheckbox, Checkbox} from './styled';

type GroupItem = {
  label: string;
  name: string;
  icon: React.ComponentProps<typeof Checkbox>['icon'];
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
    <Container>
      <GroupCheckbox
        label={groupLabel}
        id={groupLabel}
        data-testid={dataTestId}
        checked={isChecked}
        indeterminate={isIndeterminate && !isChecked}
        onCmInput={() => {
          form.batch(() => {
            items.forEach(({name}) => {
              form.change(name, !isChecked);
            });
          });
        }}
      />
      <Group>
        {items.map(({label, name, icon}) => (
          <Field name={name} component="input" type="checkbox" key={name}>
            {({input}) => (
              <Checkbox
                {...input}
                label={label}
                checked={input.checked}
                icon={icon}
                onCmInput={input.onChange}
                data-testid={`filter-${name}`}
              />
            )}
          </Field>
        ))}
      </Group>
    </Container>
  );
};

export {CheckboxGroup};
