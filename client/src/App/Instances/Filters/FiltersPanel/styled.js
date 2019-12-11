/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import VerticalCollapseButton from 'modules/components/VerticalCollapseButton';
import BasicCollapseButton from 'modules/components/CollapseButton';
import Panel from 'modules/components/Panel';
import CollapsablePanel from 'modules/components/CollapsablePanel';

export const FiltersHeader = styled(Panel.Header)`
  display: flex;
  justify-content: flex-start;

  align-items: center;
  flex-shrink: 0;
`;

export const FiltersBody = styled(CollapsablePanel.Body)`
  overflow: auto;
  overflow-x: hidden;
`;

export const VerticalButton = styled(VerticalCollapseButton)`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 0 3px 0 0;
`;

export const CollapseButton = styled(BasicCollapseButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
  z-index: 2;
`;
