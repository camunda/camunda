/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {BaseCircle, BaseBlock} from 'modules/components/Skeleton';
import styled from 'styled-components';

import * as Header from '../styled';

const SkeletonTD = styled(Header.Td)`
  display: flex;

  align-items: center;
`;

const OperationSkeletonTD = styled(Header.Td)`
  width: 250px;
`;

const Skeleton = styled.div`
  width: 100%;
  overflow: hidden;
`;

const Row = styled.div`
  display: flex;
  padding: 8px 0;
`;

const Block = styled(BaseBlock)`
  height: 14px;
`;

const InitialBlock = styled(Block)`
  width: 125px;
  margin-left: 10px;
`;

const IdBlock = styled(Block)`
  width: 120px;
`;

const VersionBlock = styled(Block)`
  width: 80px;
`;

const TimeStampBlock = styled(Block)`
  width: 155px;
`;

const RoundedBlock = styled(Block)`
  width: 45px;
  height: 20px;
  border-radius: 20px;
`;

const Circle = styled(BaseCircle)`
  height: 14px;
  width: 14px;
`;

export {
  SkeletonTD,
  OperationSkeletonTD,
  Skeleton,
  Row,
  InitialBlock,
  IdBlock,
  VersionBlock,
  TimeStampBlock,
  RoundedBlock,
  Circle,
};
