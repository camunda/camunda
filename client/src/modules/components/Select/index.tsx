/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

type Option = {
  value: string | number;
  label: string;
};

type Props = {
  options: Option[];
  placeholder: string;
} & React.SelectHTMLAttributes<HTMLSelectElement>;

const Select: React.FC<Props> = ({
  options,
  placeholder,
  disabled,
  ...props
}) => {
  return (
    <Styled.Select
      {...props}
      aria-label={placeholder}
      aria-disabled={disabled}
      disabled={disabled}
    >
      <option value="">{placeholder}</option>
      {options.map(({label, value}) => (
        <option key={value} value={value}>
          {label}
        </option>
      ))}
    </Styled.Select>
  );
};

Select.defaultProps = {
  placeholder: 'Select',
};

export default Select;
