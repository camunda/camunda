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

const ComboBox: React.FC<React.ComponentProps<typeof BaseComboBox<Item>>> =
  observer(({id, items, onChange, value, disabled, ...props}) => {
    const getItemById = (id: string) => {
      return items.find((item) => item.id === id);
    };

    return (
      <BaseComboBox<Item>
        id={id}
        items={items}
        onChange={onChange}
        selectedItem={typeof value === 'string' ? getItemById(value) : null}
        disabled={disabled || items.length === 0}
        shouldFilterItem={(data) => {
          const {inputValue, item} = data;
          return (
            inputValue !== null &&
            item.label.toLowerCase().includes(inputValue.toLowerCase())
          );
        }}
        size="sm"
        {...props}
      />
    );
  });

export {ComboBox};
