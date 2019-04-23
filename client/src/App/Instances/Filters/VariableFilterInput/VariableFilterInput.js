/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function VariableFilterInput({onFilterChange}) {
  const [name, setName] = useState('');
  const [value, setValue] = useState('');

  function handleBlur() {
    if (!name || !value) {
      return onFilterChange({variablesQuery: null});
    }

    onFilterChange({variablesQuery: {name, value}});
  }

  return (
    <Styled.VariableFilterInput>
      <Styled.TextInput
        placeholder="Variable"
        name="name"
        data-test="nameInput"
        onChange={e => setName(e.target.value)}
        onBlur={handleBlur}
      />
      <Styled.TextInput
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
  onFilterChange: PropTypes.func.isRequired
};
