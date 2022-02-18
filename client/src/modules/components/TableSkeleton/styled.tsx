/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {BaseBlock} from 'modules/components/Skeleton';

const TD = styled.td`
  padding: 9px 0 9px 5px;
`;
const TR = styled.tr`
  height: 37px;
  &:first-child {
    border-top-style: hidden;
  }
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

export {TD, TR, Block};
