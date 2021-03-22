/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';

import Checkbox from 'modules/components/Checkbox';
import {Container, Group} from './styled';

type GroupItem = {
  label: string;
  name: string;
};

type Props = {
  groupLabel: string;
  items: GroupItem[];
};

const CheckboxGroup: React.FC<Props> = ({groupLabel, items}) => {
  const form = useForm();
  const fieldValues = items.map(({name}) =>
    Boolean(form.getState().values[name])
  );
  const isChecked = fieldValues.every((value) => value);
  const isIndeterminate = fieldValues.some((value) => value);

  return (
    <Container>
      <Checkbox
        label={groupLabel}
        id={groupLabel}
        isChecked={isChecked}
        isIndeterminate={isIndeterminate && !isChecked}
        onChange={() => {
          form.batch(() => {
            items.forEach(({name}) => {
              form.change(name, !isChecked);
            });
          });
        }}
      />
      <Group>
        {items.map(({label, name}) => (
          <Field name={name} component="input" type="checkbox" key={name}>
            {({input}) => (
              <Checkbox
                id={input.name}
                isChecked={input.checked}
                onChange={input.onChange}
                label={label}
              />
            )}
          </Field>
        ))}
      </Group>
    </Container>
  );
};

export {CheckboxGroup};
