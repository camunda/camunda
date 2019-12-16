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
function Pill(props) {
  const TargetIcon = iconTypes[props.type] || (() => null);
  return (
    <Styled.Pill
      onClick={props.onClick}
      className={props.className}
      isActive={props.isActive}
      disabled={props.isDisabled}
      type={props.type}
    >
      <TargetIcon />
      <Styled.Label grow={props.grow}>{props.children}</Styled.Label>
      {props.type === PILL_TYPE.FILTER && (
        <Styled.Count>{props.count}</Styled.Count>
      )}
    </Styled.Pill>
  );
}

Pill.propTypes = {
  type: PropTypes.oneOf(Object.values(PILL_TYPE)),
  isActive: PropTypes.bool.isRequired,
  isDisabled: PropTypes.bool,
  children: PropTypes.node,
  count: PropTypes.number,
  grow: PropTypes.bool,
  onClick: PropTypes.func,
  className: PropTypes.string
};

Pill.defaultProps = {
  isActive: false,
  grow: false
};
export default React.memo(Pill);
