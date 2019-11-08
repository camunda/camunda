/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {LabeledInput} from 'components';
import './Checklist.scss';
import {t} from 'translation';

export default function Checklist({data, formatter, onChange, selectAll, deselectAll}) {
  const formattedData = data.map(formatter);
  const allSelected = formattedData.every(({checked}) => checked);
  const allDeselected = formattedData.every(({checked}) => !checked);
  return (
    <div className="Checklist">
      {formattedData.length > 1 && (
        <LabeledInput
          ref={input => {
            if (input != null) {
              input.indeterminate = !allSelected && !allDeselected;
            }
          }}
          checked={allSelected}
          type="checkbox"
          label={t('common.selectAll')}
          onChange={({target: {checked}}) => (checked ? selectAll() : deselectAll())}
        />
      )}
      <div className="itemsList">
        {formattedData.map(({id, label, checked}) => (
          <LabeledInput
            key={id}
            type="checkbox"
            checked={checked}
            label={label}
            onChange={({target: {checked}}) => onChange(id, checked)}
          />
        ))}
      </div>
    </div>
  );
}
