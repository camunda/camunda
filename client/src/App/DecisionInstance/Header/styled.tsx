/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as BaseStateCompleted} from 'modules/components/Icon/state-completed.svg';
import {ReactComponent as BaseStateIncident} from 'modules/components/Icon/state-icon-incident.svg';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const Container = styled.header`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance.header;

    return css`
      padding: 8px 65px 8px 20px;
      display: grid;
      grid-template-columns: 18px 1fr 82px;
      grid-column-gap: 10px;
      align-items: center;
      background-color: ${colors.backgroundColor};
      border-bottom: solid 1px ${theme.colors.borderColor};
    `;
  }}
`;

const Table = styled.table`
  ${({theme}) => {
    const {colors} = theme;

    return css`
      color: ${colors.text01};
      text-align: left;
      table-layout: fixed;
      width: 100%;
      border-collapse: collapse;
    `;
  }}
`;

const TH = styled.th`
  font-size: 12px;
  font-weight: 400;
  opacity: 0.9;
`;

const TD = styled.td`
  font-size: 15px;
  font-weight: 500;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  height: 20px;
`;

const EvaluatedIcon = styled(BaseStateCompleted)`
  ${({theme}) => {
    const colors = theme.colors.modules.stateIcon.completedIcon;

    return css`
      width: 18px;
      height: 18px;
      color: ${colors.color};
    `;
  }}
`;

const FailedIcon = styled(BaseStateIncident)`
  ${({theme}) => {
    return css`
      width: 18px;
      height: 18px;
      color: ${theme.colors.incidentsAndErrors};
    `;
  }}
`;

type SkeletonBlockProps = {
  $width?: string;
};

const SkeletonBlock = styled(BaseBlock)<SkeletonBlockProps>`
  ${({$width}) => {
    return css`
      width: ${$width ?? '80%'};
      height: 65%;
    `;
  }}
`;

const SkeletonCircle = styled(BaseCircle)`
  width: 18px;
  height: 18px;
`;

export {
  Container,
  Table,
  TH,
  TD,
  EvaluatedIcon,
  FailedIcon,
  SkeletonBlock,
  SkeletonCircle,
};
