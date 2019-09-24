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
  onChange,
  variable,
  checkIsComplete,
  checkIsValueValid,
  ...props
}) {
  const [isVariableComplete, setIsVariableComplete] = useState(true);
  const [isValueValid, setIsValueValid] = useState(false);

  useEffect(() => {
    setIsValueValid(checkIsValueValid(variable));

    if (checkIsComplete(variable)) {
      setIsVariableComplete(true);
    }
  }, [variable]);

  async function handleChange(event) {
    const {name, value} = event.target;
    const newVariable = {...variable, [name]: value};

    onChange(newVariable);

    await onFilterChange();

    setIsVariableComplete(checkIsComplete(newVariable));
  }

  return (
    <Styled.VariableFilterInput {...props} hasError={!isVariableComplete}>
      <Styled.TextInput
        value={variable.name}
        placeholder="Variable"
        name="name"
        data-test="nameInput"
        onChange={handleChange}
        hasError={!isVariableComplete}
      />
      <Styled.TextInput
        value={variable.value}
        placeholder="Value"
        name="value"
        data-test="valueInput"
        onChange={handleChange}
        hasError={(!isValueValid && isVariableComplete) || !isVariableComplete}
      />
      {(!isVariableComplete || !isValueValid) && (
        <Styled.WarningIcon>!</Styled.WarningIcon>
      )}
    </Styled.VariableFilterInput>
  );
}

VariableFilterInput.propTypes = {
  onFilterChange: PropTypes.func.isRequired,
  variable: PropTypes.object.isRequired,
  checkIsValueValid: PropTypes.func,
  checkIsComplete: PropTypes.func
};

VariableFilterInput.defaultProps = {
  checkIsValueValid: () => true,
  checkIsComplete: () => true
};
