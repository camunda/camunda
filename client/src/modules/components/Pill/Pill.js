/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {PILL_TYPE} from 'modules/constants';

import * as Styled from './styled';

const iconTypes = {
  [PILL_TYPE.TIMESTAMP]: Styled.Clock
};
export default function Pill(props) {
  const TargetIcon = iconTypes[props.type] || (() => null);
  return (
    <Styled.Pill {...props}>
      <TargetIcon />
      <span>{props.children}</span>
    </Styled.Pill>
  );
}

Pill.propTypes = {
  type: PropTypes.oneOf(Object.values(PILL_TYPE)),
  isActive: PropTypes.bool.isRequired,
  children: PropTypes.string.isRequired
};
