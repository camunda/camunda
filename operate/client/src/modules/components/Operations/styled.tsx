/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import styled from 'styled-components';

const OperationsContainer = styled(Stack)`
  position: relative;

  .cds--popover[role='tooltip'] {
    display: none;
  }
`;

const LoadingIndicatorContainer = styled.div`
  position: absolute;
  inset-inline-end: 100%;
  top: 50%;
  width: var(--cds-spacing-07);
  transform: translateY(-50%);
`;

export {LoadingIndicatorContainer, OperationsContainer};
