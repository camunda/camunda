/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styled} from 'styled-components';
import {breakpoints} from '@carbon/elements';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: var(--cds-layer);

  @media (max-width: ${breakpoints.lg.width}) {
    min-height: 250px;
    flex: 1;
  }
`;

const TabContent = styled.div`
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding-top: var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
`;

export {Container, TabContent};
