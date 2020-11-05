/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  options: any[];
  placeholder?: string;
  disabled?: boolean;
};

export default function Select(props: Props) {
  const {options, placeholder, ...otherProps} = props;

  return (
    <Styled.Select
      {...otherProps}
      aria-label={placeholder}
      aria-disabled={props.disabled}
    >
      <option value="">{placeholder}</option>
      {options.map((option) => {
        return (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        );
      })}
    </Styled.Select>
  );
}

Select.defaulProps = {
  placeholder: 'Select',
};
