/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';

import {t} from 'translation';
import {Checklist} from 'components';

export default function BooleanInput({changeFilter, setValid, filter}) {
  useEffect(() => {
    setValid?.(filter.values.length > 0);
  }, [filter, setValid]);

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
