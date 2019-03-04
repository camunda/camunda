/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

const Button = React.forwardRef(function Button(props, ref) {
  return <Styled.Button {...props} ref={ref} />;
});

Button.propTypes = {
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  color: PropTypes.oneOf(['main', 'primary'])
};

Button.defaultProps = {
  size: 'medium',
  color: 'main'
};

export default Button;
