/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled';

export default function Badge(props) {
  const {children, position} = props;

  const isRoundBagde =
    children && children.toString().length === 1 && position === 0;
  const Component = isRoundBagde ? Styled.BadgeCircle : Styled.Badge;

  return <Component data-test="badge" {...props} />;
}

Badge.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  type: PropTypes.oneOf(Object.keys(BADGE_TYPE)),
  isActive: PropTypes.bool
};

Badge.defaultProps = {
  isActive: true,
  /* position of Badge in ComboBadge; independent Badges have position 0 */
  position: 0
};
