/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import * as Styled from './styled';

type OwnProps = {
  onFilterChange: (...args: any[]) => any;
  variable: any;
  checkIsValueValid?: (...args: any[]) => any;
  checkIsNameComplete?: (...args: any[]) => any;
  checkIsValueComplete?: (...args: any[]) => any;
  onChange?: (...args: any[]) => any;
};

type Props = OwnProps & typeof VariableFilterInput.defaultProps;

export default function VariableFilterInput({
  onFilterChange,
  onChange,
  variable,
  checkIsNameComplete,
  checkIsValueComplete,
  checkIsValueValid,
  ...props
}: Props) {
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
  }, [variable, checkIsValueValid, checkIsValueComplete, checkIsNameComplete]);

  async function handleChange(event: any) {
    const {name, value} = event.target;
    const newVariable = {...variable, [name]: value};

    // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
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
        data-testid="nameInput"
        onChange={handleChange}
        hasError={!isNameComplete}
      />
      <Styled.TextInput
        value={variable.value}
        placeholder="Value"
        name="value"
        data-testid="valueInput"
        onChange={handleChange}
        hasError={(!isValueValid && isValueComplete) || !isValueComplete}
      />
      {(!isNameComplete || !isValueComplete || !isValueValid) && (
        <Styled.WarningIcon>!</Styled.WarningIcon>
      )}
    </Styled.VariableFilterInput>
  );
}

VariableFilterInput.defaultProps = {
  checkIsValueValid: () => true,
  checkIsNameComplete: () => true,
  checkIsValueComplete: () => true,
};
