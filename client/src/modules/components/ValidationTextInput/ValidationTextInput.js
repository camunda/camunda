/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import PropTypes from 'prop-types';
import * as Styled from './styled';

function ValidationTextInput({
  children,
  onChange,
  isComplete,
  onFilterChange,
  ...props
}) {
  const [isIncomplete, setIsIncomplete] = useState(false);

  const handleChange = async event => {
    const {value} = event.target;
    onChange(event);

    if (isComplete(value)) {
      setIsIncomplete(false);
      await onFilterChange();
    } else {
      await onFilterChange();
      setIsIncomplete(true);
    }
  };

  const handleBlur = event => {
    const {value} = event.target;

    if (!isComplete(value)) {
      setIsIncomplete(true);
    }
  };

  return (
    <Styled.InputContainer>
      <Styled.Input
        {...props}
        onChange={handleChange}
        onBlur={handleBlur}
        isIncomplete={isIncomplete}
      />
      {isIncomplete && <Styled.WarningIcon>!</Styled.WarningIcon>}
    </Styled.InputContainer>
  );
}

ValidationTextInput.propTypes = {
  onChange: PropTypes.func.isRequired,
  isComplete: PropTypes.func,
  onFilterChange: PropTypes.func
};

ValidationTextInput.defaultProps = {
  isComplete: () => true,
  onFilterChange: () => {}
};

export default ValidationTextInput;
