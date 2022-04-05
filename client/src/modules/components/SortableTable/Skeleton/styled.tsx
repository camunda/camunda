/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

type BlockProps = {
  width: string;
};

const Block = styled(BaseBlock)<BlockProps>`
  ${({width}) => {
    return css`
      height: 14px;
      width: ${width};
    `;
  }}
`;

type CircleProps = {
  width: string;
  height: string;
};

const Circle = styled(BaseBlock)<CircleProps>`
  ${({width, height}) => {
    return css`
      border-radius: 12px;
      height: ${height};
      width: ${width};
    `;
  }}
`;

const SkeletonCheckboxBlock = styled(BaseBlock)`
  height: 14px;
  width: 14px;
  border-radius: 3px;
  flex-shrink: 0;
  margin-bottom: -2px;
  margin-right: 12px;
  display: inline-block;
`;

export {TD, TR, Block, Circle, SkeletonCheckboxBlock};
