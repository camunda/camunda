/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {COMBO_BADGE_TYPE} from 'modules/constants';

import * as Styled from './styled';

export default function ComboBadge(props) {
  const children = React.Children.map(props.children, (child, index) =>
    React.cloneElement(child, {
      type: props.type,
      isActive: props.isActive,
      position: index
    })
  );
  return <Styled.ComboBadge {...props}> {children}</Styled.ComboBadge>;
}

ComboBadge.Left = function Left(props) {
  return <Styled.Left {...props} />;
};

ComboBadge.Right = function Right(props) {
  return <Styled.Right {...props} />;
};

ComboBadge.Left.prototypes = ComboBadge.Right.propTypes = {
  type: PropTypes.oneOf(Object.keys(COMBO_BADGE_TYPE))
};

ComboBadge.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  type: PropTypes.oneOf(Object.keys(COMBO_BADGE_TYPE)),
  isActive: PropTypes.bool
};

ComboBadge.defaultProps = {
  isActive: true
};
