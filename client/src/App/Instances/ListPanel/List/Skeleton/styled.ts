/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const Row = styled.div`
  display: flex;
  margin-left: 20px;
  padding-top: 20px;
`;

const Td = styled.td`
  padding: 9px 0 9px 5px;
`;

const Block = styled(BaseBlock)`
  height: 14px;
`;

const ProcessContainer = styled.div`
  padding-left: 15px;
  display: flex;
`;

const SkeletonCheckboxBlock = styled(BaseBlock)`
  height: 14px;
  width: 14px;
  border-radius: 3px;
  flex-shrink: 0;
  margin-bottom: -2px;
`;

const CircleBlock = styled(BaseCircle)`
  width: 14px;
  height: 14px;
  margin-left: 12px;
  margin-right: 10px;
  flex-shrink: 0;
`;

const ProcessBlock = styled(Block)`
  margin-left: 5px;
  flex-shrink: 0;
  width: 13vw;
`;

const InstanceIdBlock = styled(Block)`
  width: 11vw;
`;

const VersionBlock = styled(Block)`
  width: 5vw;
`;

const TimeBlock = styled(Block)`
  width: 8vw;
`;

const OperationsBlock = styled(Block)`
  width: 50px;
  height: 20px;
  border-radius: 12px;
`;

export {
  Row,
  Td,
  Block,
  ProcessContainer,
  SkeletonCheckboxBlock,
  CircleBlock,
  ProcessBlock,
  InstanceIdBlock,
  VersionBlock,
  TimeBlock,
  OperationsBlock,
};
