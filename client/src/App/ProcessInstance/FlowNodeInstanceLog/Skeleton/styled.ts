/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

import {MultiRow as BasicMultiRow} from 'modules/components/MultiRow';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const MultiRow = styled(BasicMultiRow)`
  width: 100%;
`;

const Block = styled(BaseBlock)`
  margin-left: 52px;
  height: 12px;
  flex-grow: 1;
`;

const Circle = styled(BaseCircle)`
  height: 12px;
  width: 12px;
`;

const Container = styled.div`
  display: flex;
  max-width: 300px;
  padding: 8px 10px;

  &:first-child {
    ${Block} {
      margin-left: 20px;
    }
  }
`;

export {MultiRow, Block, Circle, Container};
