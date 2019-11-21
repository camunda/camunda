/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';
import * as Header from '../styled';

// Row.propTypes = {
//   parent: PropTypes.bool
// };

export default React.memo(function Skeleton(props) {
  return (
    <Header.Table {...props}>
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
          <Styled.ActionSkeletonTD>
            <Styled.RoundedBlock />
          </Styled.ActionSkeletonTD>
        </Header.Tr>
      </tbody>
    </Header.Table>
  );
});
