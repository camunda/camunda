/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import * as Header from '../styled';

const colors = css`
  background: ${themeStyle({
    dark: 'rgba(136, 136, 141)',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.2,
    light: 0.09
  })};
`;

export const SkeletonTD = themed(styled(Header.Td)`
  display: flex;

  align-items: center;
`);

export const ActionSkeletonTD = themed(styled(Header.Td)`
  width: 250px;
`);

export const Skeleton = themed(styled.div`
  width: 100%;
  overflow: hidden;
`);

export const Row = themed(styled.div`
  display: flex;
  padding: 8px 0;
`);

export const Block = themed(styled.div`
  /* margin-left: 52px; */
  height: 12px;

  ${colors}
`);

export const InitialBlock = themed(styled(Block)`
  width: 125px;
  margin-left: 10px;
`);

export const IdBlock = themed(styled(Block)`
  width: 120px;
`);

export const VersionBlock = themed(styled(Block)`
  width: 80px;
`);

export const TimeStampBlock = themed(styled(Block)`
  width: 155px;
`);

export const RoundedBlock = themed(styled(Block)`
  width: 45px;
  border-radius: 20px;
`);

export const Circle = themed(styled.div`
  border-radius: 50%;
  height: 16px;
  width: 16px;

  ${colors};
`);
