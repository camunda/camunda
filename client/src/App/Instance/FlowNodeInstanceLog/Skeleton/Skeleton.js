/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export function Row({parent}) {
  return (
    <Styled.Row>
      <Styled.Circle />
      <Styled.Block parent={parent} />
    </Styled.Row>
  );
}

Row.propTypes = {
  parent: PropTypes.bool
};

export default React.memo(function Skeleton(props) {
  return (
    <Styled.MultiRow Component={Row} {...props}>
      <Row parent />
    </Styled.MultiRow>
  );
});
