/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

export function Row() {
  return (
    <Styled.Row>
      <Styled.Circle />
      <Styled.Block />
    </Styled.Row>
  );
}

export default React.memo(function Skeleton(props) {
  return <Styled.MultiRow Component={Row} {...props} />;
});
