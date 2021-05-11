/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';
import {BaseBlock} from 'modules/components/Skeleton';

const TRHeader = styled(Table.TR)`
  border-top: none;
  height: 38px;
`;

const THPositionStyles = css`
  position: sticky;
  top: 0;
  z-index: 1000;
`;

const OperationsTH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      ${THPositionStyles}
      width: 90px;
      background-color: ${theme.colors.ui02};
      box-shadow: inset 0 -1px 0 ${theme.colors.ui05};
    `;
  }}
`;

const TH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      ${THPositionStyles}
      background-color: ${theme.colors.ui02};
      box-shadow: inset 0 -1px 0 ${theme.colors.ui05};
    `;
  }}
`;

type CheckAllProps = {
  shouldShowOffset?: boolean;
};

const CheckAll = styled.div<CheckAllProps>`
  ${({shouldShowOffset}) => {
    return css`
      display: inline-block;
      margin-left: ${shouldShowOffset ? 15 : 16}px;
      margin-right: 28px;
    `;
  }}
`;

const THead = styled(Table.THead)`
  height: 37px;
  position: sticky;
  top: 0;
  z-index: 1000;
`;

const SkeletonCheckboxBlock = styled(BaseBlock)`
  height: 14px;
  width: 14px;
  border-radius: 3px;
  flex-shrink: 0;
  margin-bottom: -2px;
`;

export {OperationsTH, TRHeader, CheckAll, TH, THead, SkeletonCheckboxBlock};
