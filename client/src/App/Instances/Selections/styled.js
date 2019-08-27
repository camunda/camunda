/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Colors} from 'modules/theme';
import BadgeComponent from 'modules/components/Badge';
import BasicCollapsablePanel from 'modules/components/CollapsablePanel';
import BasicCollapseButton from 'modules/components/CollapseButton';
import VerticalCollapseButton from 'modules/components/VerticalCollapseButton';

export const Selections = styled.div`
  position: absolute;
  top: 0;
  right: 0;
  height: 100%;
  display: flex;
  margin-left: 1px;
  z-index: 2;
`;

export const CollapsablePanel = styled(BasicCollapsablePanel)`
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.5);
  border-radius: 3px 0px 0 0;
`;

export const SelectionHeader = styled(BasicCollapsablePanel.Header)`
  display: flex;
  justify-content: flex-start;
  padding-left: 45px;
  align-items: center;
  flex-shrink: 0;
  border-radius: 3px 0 0 0;
`;

export const SelectionBody = styled(BasicCollapsablePanel.Body)`
  overflow: auto;
`;

export const Badge = styled(BadgeComponent)`
  margin-left: 13px;
`;

export const SelectionsBadge = styled(BadgeComponent)`
  background-color: ${Colors.selections};
  color: #ffffff;
`;

export const CollapseButton = styled(BasicCollapseButton)`
  position: absolute;
  left: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
  z-index: 3;
`;

export const VerticalButton = styled(VerticalCollapseButton)`
  position: absolute;
  top: 0;
  right: 0;
  width: 100%;
  height: 100%;

  border-radius: 3px 0 0 0;
`;
