/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';
import StateIcon from 'modules/components/StateIcon';

const Block = styled(BaseBlock)`
  height: 14px;
`;

const ProcessContainer = styled.div`
  padding-left: 14px;
  display: flex;
`;

const CircleBlock = styled(BaseCircle)`
  width: 14px;
  height: 14px;
  margin-right: 10px;
  flex-shrink: 0;
`;

const ProcessBlock = styled(Block)`
  margin-left: 1px;
  flex-shrink: 0;
  width: 201px;
`;

const State = styled(StateIcon)`
  top: 0;
  margin-right: 10px;
  margin-top: auto;
  margin-bottom: auto;
  &:first-child {
    margin-left: 0;
  }
`;

export {ProcessContainer, CircleBlock, ProcessBlock, State};
