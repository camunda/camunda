/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

function VariableRow() {
  return (
    <Styled.Row>
      <Styled.VariableBlock />
      <Styled.ValueBlock />
    </Styled.Row>
  );
}

const Rows = React.memo(function Skeleton(props) {
  return (
    <div data-testid="skeleton-rows">
      {/* @ts-expect-error ts-migrate(2769) FIXME: Type '() => Element' is not assignable to type 'Re... Remove this comment to see the full error message */}
      <Styled.MultiRow Component={VariableRow} {...props} />
    </div>
  );
});

export {Rows};
