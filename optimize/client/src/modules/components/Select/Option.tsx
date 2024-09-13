/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItemSelectable} from '@carbon/react';
import {ComponentProps, ReactNode} from 'react';

import {useSelectContext} from './Select';

type OptionProps<T extends object | string | number = string> = Omit<
  ComponentProps<typeof MenuItemSelectable>,
  'label'
> & {
  label?: string | JSX.Element[];
  children?: ReactNode;
  disabled?: boolean;
  value?: T;
};

export function Option<T extends object | string | number = string>(props: OptionProps<T>) {
  const {onChange, value} = useSelectContext();
  const selected = props.value === value;

  return (
    // @ts-ignore
    // To make disabled state work, we can't pass children to it
    <MenuItemSelectable
      className="Option"
      {...props}
      label={props.label?.toString() || ''}
      onChange={onChange}
      selected={selected}
    >
      {!props.disabled && props.children}
    </MenuItemSelectable>
  );
}
