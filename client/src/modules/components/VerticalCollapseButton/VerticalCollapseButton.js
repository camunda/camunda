/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function VerticalCollapseButton({
  label,
  children,
  ...otherProps
}) {
  return (
    <Styled.Button title={`Expand ${label}`} {...otherProps}>
      <Styled.Vertical>
        <span>{label}</span>
        {children}
      </Styled.Vertical>
    </Styled.Button>
  );
}

VerticalCollapseButton.propTypes = {
  label: PropTypes.string.isRequired,
  children: PropTypes.node
};
