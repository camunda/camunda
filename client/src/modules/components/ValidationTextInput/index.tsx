/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {InputContainer, Warning} from './styled';

type Props = {
  onChange: (...args: any[]) => any;
  checkIsComplete: (...args: any[]) => any;
  checkIsValid: (...args: any[]) => any;
  onFilterChange: (...args: any[]) => any;
  name?: string;
  value?: string;
  children?: React.ReactNode;
  errorMessage?: string;
  placeholder?: string;
};

function ValidationTextInput({
  children,
  onChange,
  checkIsComplete,
  checkIsValid,
  onFilterChange,
  name,
  value,
  errorMessage,
  ...props
}: Props) {
  const [isComplete, setIsComplete] = useState(true);
  const [isValid, setIsValid] = useState(true);

  useEffect(() => {
    setIsValid(checkIsValid(value));

    if (checkIsComplete(value)) setIsComplete(true);
  }, [checkIsComplete, checkIsValid, value]);

  const handleChange = async (event: any) => {
    const {value} = event.target;

    onChange(event);

    await onFilterChange();

    if (!checkIsComplete(value)) {
      setIsComplete(false);
    }
  };

  const handleBlur = (event: any) => {
    const {value} = event.target;
    if (!checkIsComplete(value)) {
      setIsComplete(false);
    }
  };

  return (
    <InputContainer>
      {React.Children.map(children, (child) =>
        // @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
        React.cloneElement(child, {
          ...props,
          name,
          value,
          onChange: handleChange,
          onBlur: handleBlur,
          $hasError: !isValid || !isComplete,
        })
      )}
      {(!isComplete || !isValid) && <Warning title={errorMessage} />}
    </InputContainer>
  );
}

ValidationTextInput.defaultProps = {
  checkIsComplete: () => true,
  checkIsValid: () => true,
  onFilterChange: () => {},
};

export {ValidationTextInput};
