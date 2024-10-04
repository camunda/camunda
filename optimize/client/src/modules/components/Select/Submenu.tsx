/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItemSelectable} from '@carbon/react';
import {ComponentProps, ReactNode, useEffect, useState} from 'react';

import {ignoreFragments, isReactElement} from 'services';

import Select, {SelectContext, useSelectContext} from './Select';

type SubmenuProps = Omit<ComponentProps<typeof MenuItemSelectable>, 'label'> & {
  label?: string | JSX.Element[];
  children?: ReactNode;
  disabled?: boolean;
};

export function Submenu(props: SubmenuProps) {
  const childrenArray = ignoreFragments(props.children);
  const [filteredChildren, setFilteredChildren] = useState(childrenArray);
  const {onChange, value} = useSelectContext();
  const selected = childrenArray.some(
    (child) => isReactElement(child) && child.type !== Select.Search && child.props.value === value
  );

  useEffect(() => {
    setFilteredChildren(ignoreFragments(props.children));
  }, [props.children]);

  return (
    <SelectContext.Provider
      value={{
        children: childrenArray,
        filteredChildren,
        setFilteredChildren,
        onChange: props.onChange || onChange,
        value: props.value || value,
      }}
    >
      <MenuItemSelectable
        className="Submenu"
        {...props}
        label={props.label?.toString() || ''}
        selected={selected}
      >
        {!props.disabled && filteredChildren}
      </MenuItemSelectable>
    </SelectContext.Provider>
  );
}
