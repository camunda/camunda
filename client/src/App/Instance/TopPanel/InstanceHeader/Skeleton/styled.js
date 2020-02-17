/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {BaseCircle, BaseBlock} from 'modules/components/Skeleton';
import styled from 'styled-components';

import * as Header from '../styled';

export const SkeletonTD = styled(Header.Td)`
  display: flex;

  align-items: center;
`;

export const ActionSkeletonTD = styled(Header.Td)`
  width: 250px;
`;

export const Skeleton = styled.div`
  width: 100%;
  overflow: hidden;
`;

export const Row = styled.div`
  display: flex;
  padding: 8px 0;
`;

export const Block = styled(BaseBlock)`
  height: 14px;
`;

export const InitialBlock = styled(Block)`
  width: 125px;
  margin-left: 10px;
`;

export const IdBlock = styled(Block)`
  width: 120px;
`;

export const VersionBlock = styled(Block)`
  width: 80px;
`;

export const TimeStampBlock = styled(Block)`
  width: 155px;
`;

export const RoundedBlock = styled(Block)`
  width: 45px;
  height: 20px;
  border-radius: 20px;
`;

export const Circle = styled(BaseCircle)`
  height: 14px;
  width: 14px;
`;
