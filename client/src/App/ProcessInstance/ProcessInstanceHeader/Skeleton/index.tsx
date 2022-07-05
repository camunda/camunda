/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';
import * as Header from '../styled';

export default React.memo(function Skeleton(props) {
  return (
    <>
      <Styled.CircleWrapper>
        <Styled.Circle />
      </Styled.CircleWrapper>
      <Header.Table {...props} data-testid="instance-header-skeleton">
        <thead>
          <tr>
            <Header.Th>Process</Header.Th>
            <Header.Th>Instance Id</Header.Th>
            <Header.Th>Version</Header.Th>
            <Header.Th>Start Date</Header.Th>
            <Header.Th>End Date</Header.Th>
            <Header.Th>Parent Process Instance Key</Header.Th>
            <Header.Th>Called Instances</Header.Th>
          </tr>
        </thead>
        <tbody>
          <tr>
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
            <Header.Td>
              <Styled.TimeStampBlock />
            </Header.Td>
            <Header.Td>
              <Styled.IdBlock />
            </Header.Td>
            <Header.Td>
              <Styled.CalledInstanceBlock />
            </Header.Td>
          </tr>
        </tbody>
      </Header.Table>
      <Styled.RoundedBlock />
    </>
  );
});
