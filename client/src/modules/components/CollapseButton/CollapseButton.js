/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {DIRECTION} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [DIRECTION.UP]: Styled.Up,
  [DIRECTION.DOWN]: Styled.Down,
  [DIRECTION.LEFT]: Styled.Left,
  [DIRECTION.RIGHT]: Styled.Right
};

export default function CollapseButton({direction, onClick, ...props}) {
  const TargetIcon = iconsMap[direction];

  return (
    <Styled.CollapseButton {...props} onClick={onClick}>
      <TargetIcon />
    </Styled.CollapseButton>
  );
}

CollapseButton.propTypes = {
  direction: PropTypes.oneOf(Object.keys(DIRECTION)),
  onClick: PropTypes.func
};
