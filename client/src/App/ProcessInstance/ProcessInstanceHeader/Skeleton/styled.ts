/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BaseCircle, BaseBlock} from 'modules/components/Skeleton';
import styled from 'styled-components';

import * as Header from '../styled';

const SkeletonTD = styled(Header.Td)`
  display: flex;

  align-items: center;
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
  margin-top: 3px;
  height: 16px;
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
  margin-right: 37px;
`;

const CalledInstanceBlock = styled(Block)`
  width: 80px;
`;

const Circle = styled(BaseCircle)`
  height: 21px;
  width: 21px;
`;

const CircleWrapper = styled.div`
  margin-top: 4px;
  margin-right: 13px;
`;

export {
  SkeletonTD,
  Skeleton,
  Row,
  InitialBlock,
  IdBlock,
  VersionBlock,
  TimeStampBlock,
  RoundedBlock,
  CalledInstanceBlock,
  Circle,
  CircleWrapper,
};
