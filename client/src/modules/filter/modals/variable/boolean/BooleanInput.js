/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Checklist} from 'components';

export default function BooleanInput({changeFilter, setValid, filter}) {
  const updateValues = (newValues) => {
    changeFilter({
      values: newValues,
    });
    setValid(newValues.length > 0);
  };

  const formatValue = (value) =>
    value === null
      ? t('common.filter.variableModal.bool.isNullOrUndefined')
      : t('common.filter.variableModal.bool.' + value.toString());

  return (
    <div className="BooleanInput">
      <Checklist
        selectedItems={filter.values}
        allItems={[true, false, null]}
        onChange={updateValues}
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
