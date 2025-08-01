/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Field, useForm} from 'react-final-form';
import {Checkbox as CarbonCheckbox, Stack} from '@carbon/react';
import {Checkbox} from 'modules/components/Checkbox';
import {Group} from './styled';
import type {CarbonIconType} from '@carbon/react/icons';

type GroupItem = {
  label: string;
  name: string;
  Icon: CarbonIconType;
};

type Props = {
  dataTestId: string;
  groupLabel: string;
  items: GroupItem[];
};

const CheckboxGroup: React.FC<Props> = ({dataTestId, groupLabel, items}) => {
  const form = useForm();
  const fieldValues = items.map(({name}) =>
    Boolean(form.getState().values[name]),
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
