/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';
import {BREAKPOINTS} from 'modules/constants';

const BottomPanel = styled.div`
  height: 100%;
  width: 100%;
  position: relative;
  z-index: 1;
`;

const BottomPanelStacked = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
`;

const ModificationFooter = styled.div`
  display: flex;
  justify-content: space-between;
  background-color: var(--cds-layer-01);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-top: 1px solid var(--cds-border-subtle-01);
`;

const Buttons = styled(Stack)`
  margin-left: auto;
`;

export {BottomPanel, BottomPanelStacked, ModificationFooter, Buttons};
