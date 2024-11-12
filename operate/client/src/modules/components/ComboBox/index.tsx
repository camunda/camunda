/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {ComboBox as BaseComboBox} from '@carbon/react';

type Item = {id: string; label: string};
type Props = {
  id: string;
  value: string;
  titleText?: string;
  placeholder?: string;
  onChange: (data: {[selectedItem: string]: Item | null | undefined}) => void;
  items: Item[];
  disabled?: boolean;
  title?: string;
};

const ComboBox: React.FC<Props> = observer(
  ({id, items, onChange, value, disabled, ...props}) => {
    const getItemById = (id: string) => {
      return items.find((item) => item.id === id);
    };

    return (
      <BaseComboBox
        id={id}
        items={items}
        onChange={onChange}
        selectedItem={getItemById(value) ?? null}
        disabled={disabled || items.length === 0}
        shouldFilterItem={(data) => {
          const {inputValue, item} = data;
          return (
            inputValue !== undefined &&
            item.label.toLowerCase().includes(inputValue?.toLowerCase())
          );
        }}
        size="sm"
        {...props}
      />
    );
  },
);

export {ComboBox};
