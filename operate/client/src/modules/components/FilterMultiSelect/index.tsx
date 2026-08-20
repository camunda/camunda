/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {MultiSelect} from '@carbon/react';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';

interface FilterMultiselectProps {
  name: string;
  titleText: string;
  items: string[];
}

const FilterMultiselect: React.FC<FilterMultiselectProps> = ({
  name,
  titleText,
  items,
}) => {
  return (
    <Field name={name}>
      {({input}) => {
        let itemsArray;
        if (typeof input.value === 'string') {
          itemsArray = input.value ? input.value.split(',') : [];
        } else {
          itemsArray = input.value;
        }

        return (
          <MultiSelect
            id={name}
            data-testid={name}
            items={items}
            selectedItems={itemsArray}
            itemToString={(selectedItem) => spaceAndCapitalize(selectedItem)}
            label="Choose option(s)"
            useTitleInItem={false}
            titleText={titleText}
            onChange={({selectedItems}) => {
              input.onChange(selectedItems?.length ? selectedItems : undefined);
            }}
            size="sm"
          />
        );
      }}
    </Field>
  );
};

export {FilterMultiselect};
