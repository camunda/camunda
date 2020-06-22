/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function PanelFooter(props) {
  const {children} = props;
  return <Styled.Footer {...props}>{children}</Styled.Footer>;
}

PanelFooter.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};
