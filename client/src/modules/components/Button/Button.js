/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {SIZE, COLOR} from './constants';
import * as Styled from './styled';

const Button = React.forwardRef(function Button(props, ref) {
  return <Styled.Button {...props} ref={ref} />;
});

Button.propTypes = {
  size: PropTypes.oneOf(Object.values(SIZE)),
  color: PropTypes.oneOf(Object.values(COLOR))
};

Button.defaultProps = {
  size: SIZE.MEDIUM,
  color: COLOR.MAIN
};

export default Button;
