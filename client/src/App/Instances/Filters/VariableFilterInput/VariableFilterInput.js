/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function VariableFilterInput({
  onFilterChange,
  variable,
  ...props
}) {
  const [name, setName] = useState('');
  const [value, setValue] = useState('');

  useEffect(
    () => {
      setName(variable.name);
      setValue(variable.value);
    },
    [variable]
  );

  function handleBlur() {
    if (!name || !value) {
      return onFilterChange({variable: null});
    }

    onFilterChange({variable: {name, value}});
  }

  return (
    <Styled.VariableFilterInput {...props}>
      <Styled.TextInput
        value={name}
        placeholder="Variable"
        name="name"
        data-test="nameInput"
        onChange={e => setName(e.target.value)}
        onBlur={handleBlur}
      />
      <Styled.TextInput
        value={value}
        placeholder="Value"
        name="value"
        data-test="valueInput"
        onChange={e => setValue(e.target.value)}
        onBlur={handleBlur}
      />
    </Styled.VariableFilterInput>
  );
}

VariableFilterInput.propTypes = {
  onFilterChange: PropTypes.func.isRequired,
  variable: PropTypes.object
};
