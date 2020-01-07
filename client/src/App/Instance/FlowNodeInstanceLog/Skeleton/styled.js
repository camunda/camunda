/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import BasicMultiRow from 'modules/components/MultiRow';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

export const MultiRow = styled(BasicMultiRow)`
  width: 100%;
`;

export const Block = styled(BaseBlock)`
  margin-left: 52px;
  height: 12px;
  flex-grow: 1;
`;

export const Circle = styled(BaseCircle)`
  height: 12px;
  width: 12px;
`;

export const Row = styled.div`
  display: flex;
  max-width: 300px;
  padding: 8px 10px;

  &:first-child {
    ${Block} {
      margin-left: 20px;
    }
  }
`;
