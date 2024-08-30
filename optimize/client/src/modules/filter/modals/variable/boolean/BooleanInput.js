/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import {t} from 'translation';
import {Checklist} from 'components';

export default function BooleanInput({changeFilter, filter}) {
  const formatValue = (value) =>
    value === null
      ? t('common.filter.variableModal.bool.isNullOrUndefined')
      : t('common.filter.variableModal.bool.' + value.toString());

  return (
    <div className="BooleanInput">
      <Checklist
        selectedItems={filter.values}
        allItems={[true, false, null]}
        onChange={(values) => {
          changeFilter({values});
        }}
        formatter={(values, selectedValues) =>
          values.map((value) => ({
            id: value,
            label: formatValue(value),
            checked: selectedValues.includes(value),
          }))
        }
        headerHidden
      />
    </div>
  );
}

BooleanInput.defaultFilter = {values: []};

BooleanInput.isValid = ({values}) => values.length > 0;
