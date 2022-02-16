/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const DecisionContainer = styled.div`
  display: flex;
`;

const CircleBlock = styled(BaseCircle)`
  width: 14px;
  height: 14px;
  margin-right: 6px;
`;

const DecisionBlock = styled(BaseBlock)`
  height: 14px;
  margin-left: 5px;
  width: 201px;
`;

export {DecisionContainer, DecisionBlock, CircleBlock};
