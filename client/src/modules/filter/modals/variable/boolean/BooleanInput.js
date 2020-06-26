/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {t} from 'translation';
import {TypeaheadMultipleSelection} from 'components';

export default function BooleanInput({changeFilter, setValid, filter}) {
  const toggleValue = (value, checked) => {
    let newValues;
    if (checked) {
      newValues = filter.values.concat(value);
    } else {
      newValues = filter.values.filter((existingValue) => existingValue !== value);
    }

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
      <TypeaheadMultipleSelection
        availableValues={[true, false, null]}
        selectedValues={filter.values}
        toggleValue={toggleValue}
        format={formatValue}
        labels={{
          available: t('common.filter.variableModal.multiSelect.available'),
          selected: t('common.filter.variableModal.multiSelect.selected'),
          empty: t('common.filter.variableModal.multiSelect.empty'),
        }}
        hideSearch
      />
    </div>
  );
}

BooleanInput.defaultFilter = {values: []};
