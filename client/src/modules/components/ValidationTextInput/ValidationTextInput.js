/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

function ValidationTextInput({
  children,
  onChange,
  checkIsComplete,
  checkIsValid,
  onFilterChange,
  name,
  value,
  ...props
}) {
  const [isComplete, setIsComplete] = useState(true);
  const [isValid, setIsValid] = useState(true);

  useEffect(() => {
    setIsValid(checkIsValid(value));

    if (checkIsComplete(value)) setIsComplete(true);
  }, [value]);

  const handleChange = async event => {
    const {value} = event.target;

    onChange(event);

    await onFilterChange();

    if (!checkIsComplete(value)) {
      setIsComplete(false);
    }
  };

  const handleBlur = event => {
    const {value} = event.target;
    if (!checkIsComplete(value)) {
      setIsComplete(false);
    }
  };

  return (
    <Styled.InputContainer>
      {React.Children.map(children, child =>
        React.cloneElement(child, {
          ...props,
          name,
          value,
          onChange: handleChange,
          onBlur: handleBlur,
          hasError: !isValid || !isComplete
        })
      )}
      {(!isComplete || !isValid) && <Styled.WarningIcon>!</Styled.WarningIcon>}
    </Styled.InputContainer>
  );
}

ValidationTextInput.propTypes = {
  onChange: PropTypes.func.isRequired,
  checkIsComplete: PropTypes.func,
  checkIsValid: PropTypes.func,
  onFilterChange: PropTypes.func,
  name: PropTypes.string,
  value: PropTypes.string,
  children: PropTypes.node
};

ValidationTextInput.defaultProps = {
  checkIsComplete: () => true,
  checkIsValid: () => true,
  onFilterChange: () => {}
};

export default ValidationTextInput;
