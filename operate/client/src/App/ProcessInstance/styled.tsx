/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';
import {INSTANCE_HISTORY_MIN_WIDTH} from 'modules/constants';

const BottomPanel = styled.div`
  height: 100%;
  width: 100%;
  position: relative;
  border-top: 1px solid var(--cds-border-subtle-01);

  /* Prevents the ElementInstanceLog from shrinking below its minimum width when
  users resize the browser window (configured dragging min-widths only apply during dragging).
  Applying the style to the ElementInstanceLog "Container" class sadly does not work. */
  & .HorizontalPanel:first-child {
    min-width: ${INSTANCE_HISTORY_MIN_WIDTH}px;
  }
`;

const BottomPanelStacked = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow-y: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
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
