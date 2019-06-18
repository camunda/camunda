/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';

import React from 'react';
import PropTypes from 'prop-types';

export default function PanelListItem({children, ...props}) {
  return <Styled.PanelListItem {...props}>{children}</Styled.PanelListItem>;
}

PanelListItem.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
