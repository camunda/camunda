/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import EmptyMessage from '../EmptyMessage';
import {default as SplitPaneComponent} from 'modules/components/SplitPane';

export const EmptyMessageWrapper = styled.div`
  position: relative;
`;

export const DiagramEmptyMessage = styled(EmptyMessage)`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

export const PaneHeader = styled(SplitPaneComponent.Pane.Header)`
  border-radius: 3px 3px 0 0;
`;
