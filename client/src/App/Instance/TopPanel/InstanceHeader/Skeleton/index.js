/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';
import * as Header from '../styled';

export default React.memo(function Skeleton(props) {
  return (
    <Header.Table {...props} data-test="instance-header-skeleton">
      <tbody>
        <Header.Tr>
          <Styled.SkeletonTD>
            <Styled.Circle />
            <Styled.InitialBlock />
          </Styled.SkeletonTD>
          <Header.Td>
            <Styled.IdBlock />
          </Header.Td>
          <Header.Td>
            <Styled.VersionBlock />
          </Header.Td>
          <Header.Td>
            <Styled.TimeStampBlock />
          </Header.Td>
          <Header.Td>
            <Styled.TimeStampBlock />
          </Header.Td>
          <Styled.OperationSkeletonTD>
            <Styled.RoundedBlock />
          </Styled.OperationSkeletonTD>
        </Header.Tr>
      </tbody>
    </Header.Table>
  );
});
