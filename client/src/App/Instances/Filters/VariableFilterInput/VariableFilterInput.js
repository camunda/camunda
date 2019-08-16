/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function VariableFilterInput({
  onFilterChange,
  onChange,
  variable,
  ...props
}) {
  function handleBlur() {
    if (!variable.name || !variable.value) {
      return onFilterChange({variable: null});
    }

    onFilterChange({variable});
  }

  function handleChange(event) {
    const {name, value} = event.target;
    onChange({...variable, [name]: value});
  }

  return (
    <Styled.VariableFilterInput {...props}>
      <Styled.TextInput
        value={variable.name}
        placeholder="Variable"
        name="name"
        data-test="nameInput"
        onChange={handleChange}
        onBlur={handleBlur}
      />
      <Styled.TextInput
        value={variable.value}
        placeholder="Value"
        name="value"
        data-test="valueInput"
        onChange={handleChange}
        onBlur={handleBlur}
      />
    </Styled.VariableFilterInput>
  );
}

VariableFilterInput.propTypes = {
  onFilterChange: PropTypes.func.isRequired,
  variable: PropTypes.object
};
