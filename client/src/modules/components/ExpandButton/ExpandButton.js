/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function ExpandButton({children, isExpanded, ...props}) {
  return (
    <Styled.Button {...props}>
      <Styled.Icon expandTheme={props.expandTheme}>
        {isExpanded ? <Styled.DownIcon /> : <Styled.RightIcon />}
      </Styled.Icon>
      {children}
    </Styled.Button>
  );
}

ExpandButton.propTypes = {
  isExpanded: PropTypes.bool,
  expandTheme: PropTypes.string.isRequired
};
