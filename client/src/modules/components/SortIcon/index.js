/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {SORT_ORDER} from 'modules/constants';
import * as Styled from './styled';

export default function SortIcon(props) {
  const TargetIcon =
    props.sortOrder === SORT_ORDER.ASC ? Styled.Up : Styled.Down;
  return (
    <Styled.SortIcon {...props} data-test={`${props.sortOrder}-icon`}>
      <TargetIcon data-test="sort-icon" sortOrder={props.sortOrder} />
    </Styled.SortIcon>
  );
}

SortIcon.propTypes = {
  sortOrder: PropTypes.oneOf(Object.values(SORT_ORDER)),
  onClick: PropTypes.func,
  disabled: PropTypes.bool,
};
SortIcon.defaultProps = {
  disabled: PropTypes.false,
};
