/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import MultiRow from 'modules/components/MultiRow';

function InstanceRow() {
  return (
    <tr>
      <Styled.Td>
        <Styled.ProcessContainer>
          <Styled.SkeletonCheckboxBlock />
          <Styled.CircleBlock />
          <Styled.ProcessBlock />
        </Styled.ProcessContainer>
      </Styled.Td>
      <Styled.Td>
        <Styled.InstanceIdBlock />
      </Styled.Td>
      <Styled.Td>
        <Styled.VersionBlock />
      </Styled.Td>
      <Styled.Td>
        <Styled.TimeBlock />
      </Styled.Td>
      <Styled.Td>
        <Styled.TimeBlock />
      </Styled.Td>
      <Styled.Td>
        <Styled.OperationsBlock />
      </Styled.Td>
    </tr>
  );
}

function Skeleton(props: any) {
  return (
    <tbody data-testid="listpanel-skeleton">
      <MultiRow Component={InstanceRow} {...props} />
    </tbody>
  );
}

export {Skeleton};
