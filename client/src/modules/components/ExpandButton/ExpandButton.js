/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';
import IconButton from 'modules/components/IconButton';

const ExpandButton = React.forwardRef(function ExpandButton(
  {children, isExpanded, ...props},
  ref
) {
  const renderIcon = () => (
    <Styled.Transition timeout={400} in={isExpanded} appear>
      <Styled.ArrowIcon />
    </Styled.Transition>
  );

  return (
    <IconButton {...props} icon={renderIcon()}>
      {children}
    </IconButton>
  );
});

ExpandButton.propTypes = {
  isExpanded: PropTypes.bool
};

ExpandButton.defaultProps = {
  isExpanded: false
};

export default ExpandButton;
