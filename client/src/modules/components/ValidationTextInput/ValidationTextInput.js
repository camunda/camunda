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
  onFilterChange,
  name,
  value,
  ...props
}) {
  const [isIncomplete, setIsIncomplete] = useState(false);

  useEffect(() => {
    if (checkIsComplete(value)) setIsIncomplete(false);
  }, [value]);

  const handleChange = async event => {
    const {value} = event.target;

    onChange(event);

    await onFilterChange();

    if (!checkIsComplete(value)) {
      setIsIncomplete(true);
    }
  };

  const handleBlur = event => {
    const {value} = event.target;
    if (!checkIsComplete(value)) {
      setIsIncomplete(true);
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
          hasError: isIncomplete
        })
      )}
      {isIncomplete && <Styled.WarningIcon>!</Styled.WarningIcon>}
    </Styled.InputContainer>
  );
}

ValidationTextInput.propTypes = {
  onChange: PropTypes.func.isRequired,
  checkIsComplete: PropTypes.func,
  onFilterChange: PropTypes.func,
  name: PropTypes.string,
  value: PropTypes.string,
  children: PropTypes.node
};

ValidationTextInput.defaultProps = {
  checkIsComplete: () => true,
  onFilterChange: () => {}
};

export default ValidationTextInput;
