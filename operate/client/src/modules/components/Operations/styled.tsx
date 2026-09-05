/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import styled from 'styled-components';

const OperationsContainer = Stack;

const LoadingSlot = styled.li`
  align-items: center;
  block-size: 2rem;
  display: flex;
  flex: 0 0 2rem;
  inline-size: 2rem;
`;

const EmptyOperationSlot = styled.li.attrs({
  'aria-hidden': true,
})`
  block-size: 2rem;
  flex: 0 0 2rem;
  inline-size: 2rem;
`;

export {EmptyOperationSlot, LoadingSlot, OperationsContainer};
