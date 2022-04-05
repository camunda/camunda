/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {SkeletonCheckboxBlock as BaseSkeletonCheckboxBlock} from './Skeleton';

const Container = styled.section`
  ${({theme}) => {
    const colors = theme.colors.sortableTable;

    return css`
      border: 1px solid ${theme.colors.borderColor};
      background-color: ${colors.backgroundColor};
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      height: 100%;
    `;
  }}
`;

const List = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

type ScrollableContentProps = {
  overflow: 'auto' | 'hidden';
};

const ScrollableContent = styled.div<ScrollableContentProps>`
  ${({overflow}) => {
    return css`
      width: 100%;
      height: 100%;
      overflow-y: ${overflow};
      flex: 1 0 0;
      position: relative;
    `;
  }}
`;

const Spinner = styled(SpinnerSkeleton)`
  margin-top: 36px;
`;

const TH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      font-weight: 500;
      white-space: nowrap;
      color: ${theme.colors.text01};
      box-shadow: inset 0 -1px 0 ${theme.colors.borderColor};
      &:first-child {
        padding-left: 19px;
      }
    `;
  }}
`;

const TRHeader = styled(Table.TR)`
  border-top: none;
`;

const THead = styled(Table.THead)`
  position: sticky;
  z-index: 2;
  top: 0;
`;

const SkeletonCheckboxBlock = styled(BaseSkeletonCheckboxBlock)`
  margin-right: 15px;
`;

export {
  Container,
  List,
  ScrollableContent,
  Table,
  Spinner,
  TH,
  TRHeader,
  THead,
  SkeletonCheckboxBlock,
};
