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
  checkIsNameComplete,
  checkIsValueComplete,
  checkIsValueValid,
  ...props
}) {
  const [isNameComplete, setIsNameComplete] = useState(true);
  const [isValueComplete, setIsValueComplete] = useState(true);
  const [isValueValid, setIsValueValid] = useState(false);

  useEffect(() => {
    setIsValueValid(checkIsValueValid(variable));

    if (checkIsValueComplete(variable)) {
      setIsValueComplete(true);
    }

    if (checkIsNameComplete(variable)) {
      setIsNameComplete(true);
    }
  }, [variable]);

  async function handleChange(event) {
    const {name, value} = event.target;
    const newVariable = {...variable, [name]: value};

    onChange(newVariable);

    await onFilterChange();

    setIsNameComplete(checkIsNameComplete(newVariable));
    setIsValueComplete(checkIsValueComplete(newVariable));
  }

  return (
    <Styled.VariableFilterInput {...props}>
      <Styled.TextInput
        value={variable.name}
        placeholder="Variable"
        name="name"
        data-test="nameInput"
        onChange={handleChange}
        hasError={!isNameComplete}
      />
      <Styled.TextInput
        value={variable.value}
        placeholder="Value"
        name="value"
        data-test="valueInput"
        onChange={handleChange}
        hasError={(!isValueValid && isValueComplete) || !isValueComplete}
      />
      {(!isNameComplete || !isValueComplete || !isValueValid) && (
        <Styled.WarningIcon>!</Styled.WarningIcon>
      )}
    </Styled.VariableFilterInput>
  );
}

VariableFilterInput.propTypes = {
  onFilterChange: PropTypes.func.isRequired,
  variable: PropTypes.object.isRequired,
  checkIsValueValid: PropTypes.func,
  checkIsNameComplete: PropTypes.func,
  checkIsValueComplete: PropTypes.func
};

VariableFilterInput.defaultProps = {
  checkIsValueValid: () => true,
  checkIsNameComplete: () => true,
  checkIsValueComplete: () => true
};
