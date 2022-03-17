/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {CmSelect} from '@camunda-cloud/common-ui-react';
import React from 'react';

type Props = {
  onCmInput: (event: {detail: {selectedOptions: string[]}}) => {};
  options: React.ComponentProps<typeof CmSelect>['options'];
  selectedOptions: React.ComponentProps<typeof CmSelect>['selectedOptions'];
  indeterminate: boolean;
  label: React.ComponentProps<typeof CmSelect>['label'];
};

const Select: React.FC<Props> = ({
  onCmInput,
  options,
  selectedOptions,
  indeterminate,
  label,
  ...props
}) => {
  return (
    <label>
      {label}
      <select
        onChange={(event) => {
          onCmInput({detail: {selectedOptions: [event.target.value]}});
        }}
        value={selectedOptions?.[0]}
        {...props}
      >
        {options !== undefined &&
          options.length > 0 &&
          options[0]?.options.map(({label, value}) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
      </select>
    </label>
  );
};

export {Select};
