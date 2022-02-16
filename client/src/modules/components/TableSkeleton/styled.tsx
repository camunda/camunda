/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const Td = styled.td`
  padding: 9px 0 9px 5px;
`;

type Props = {
  width: string;
};

const Block = styled(BaseBlock)<Props>`
  ${({width}) => {
    return css`
      height: 14px;
      width: ${width};
    `;
  }}
`;

const DecisionContainer = styled.div`
  padding-left: 15px;
  display: flex;
`;

const Circle = styled(BaseCircle)`
  width: 14px;
  height: 14px;
  margin-right: 10px;
`;

export {Td, Block, DecisionContainer, Circle};
