/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import MultiRow from 'modules/components/MultiRow';

export function InstanceRow() {
  return (
    <tr>
      <Styled.td>
        <Styled.WorkflowContainer>
          <Styled.SkeletonCheckboxBlock />
          <Styled.CircleBlock />
          <Styled.WorkflowBlock />
        </Styled.WorkflowContainer>
      </Styled.td>
      <Styled.td>
        <Styled.InstanceIdBlock />
      </Styled.td>
      <Styled.td>
        <Styled.VersionBlock />
      </Styled.td>
      <Styled.td>
        <Styled.TimeBlock />
      </Styled.td>
      <Styled.td>
        <Styled.TimeBlock />
      </Styled.td>
      <Styled.td>
        <Styled.ActionsBlock />
      </Styled.td>
    </tr>
  );
}

export default React.memo(function Skeleton(props) {
  return (
    <tbody>
      <MultiRow Component={InstanceRow} {...props} />
    </tbody>
  );
});
