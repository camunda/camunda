/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import SplitPane from 'modules/components/SplitPane';
import EmptyMessage from '../EmptyMessage';

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

export const Pane = styled(SplitPane.Pane)`
  border-radius: 3px 3px 0 0;
`;

export const PaneHeader = styled(SplitPane.Pane.Header)`
  border-radius: 3px 3px 0 0;
`;
