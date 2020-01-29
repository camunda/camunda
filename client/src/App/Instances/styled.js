/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {default as SplitPaneComponent} from 'modules/components/SplitPane';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

import {HEADER_HEIGHT} from './../Header/styled';

export const Instances = styled.main`
  height: calc(100vh - ${HEADER_HEIGHT}px);
  position: relative;
  overflow: hidden;
`;

export const Content = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%
  width: calc(100% - ${COLLAPSABLE_PANEL_MIN_WIDTH});
`;

export const FilterSection = styled.div`
  margin-right: 1px;
`;

export const SplitPane = styled(SplitPaneComponent)`
  width: 100%;
`;

export const Pane = styled(SplitPane.Pane)`
  border-radius: 3px 3px 0 0;
`;

export const PaneHeader = styled(SplitPane.Pane.Header)`
  border-radius: 3px 3px 0 0;
`;
