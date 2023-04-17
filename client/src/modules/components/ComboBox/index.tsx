/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {ComboBox as BaseComboBox} from '@carbon/react';

type Item = {id: string; label: string};
type Props = {
  id: string;
  value: string;
  titleText: string;
  placeholder?: string;
  onChange: (data: {[selectedItem: string]: Item | null | undefined}) => void;
  items: Item[];
};

const ComboBox: React.FC<Props> = observer(
  ({id, items, onChange, value, ...props}) => {
    const getItemById = (id: string) => {
      return items.find((item) => item.id === id);
    };

    return (
      <BaseComboBox
        id={id}
        items={items}
        onChange={onChange}
        selectedItem={getItemById(value) ?? null}
        disabled={items.length === 0}
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
  }
);

export {ComboBox};
