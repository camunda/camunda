/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import * as Styled from './styled';

const VariableRow: React.FC = () => {
  return (
    <Styled.Row>
      <Styled.VariableBlock />
      <Styled.ValueBlock />
    </Styled.Row>
  );
};

const Rows: React.FC = React.memo(function Skeleton(props) {
  return (
    <div data-testid="skeleton-rows">
      <Styled.MultiRow Component={VariableRow} {...props} />
    </div>
  );
});

export {Rows};
