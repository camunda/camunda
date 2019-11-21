/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

export function VariableRow() {
  return (
    <Styled.Row>
      <Styled.VariableBlock />
      <Styled.ValueBlock />
    </Styled.Row>
  );
}

export default React.memo(function Skeleton({rowsToDisplay, ...props}) {
  function rowMultiplier(rowsToDisplay) {
    const rows = [];

    for (var i = 0; i < rowsToDisplay; i++) {
      rows.push(<VariableRow key={i} />);
    }
    return rows;
  }

  return (
    <Styled.Skeleton {...props}>{rowMultiplier(rowsToDisplay)}</Styled.Skeleton>
  );
});
