/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {default as PanelComponent} from 'modules/components/Panel';

import bgBlack from 'modules/components/ZeebraStripe/tree-view-bg-dark.png';
import bgLight from 'modules/components/ZeebraStripe/tree-view-bg-light.png';

export const Panel = styled(PanelComponent)`
  border-right: none;
`;

export const NodeContainer = themed(styled.div`
  background-image: ${themeStyle({
    dark: `url(${bgBlack})`,
    light: `url(${bgLight})`
  })};

  position: absolute;
  width: inherit;
  min-width: 100%;
  min-height: min-content;
  margin: 0;
  padding: 0;
  padding-left: 8px;
`);

export const FlowNodeInstanceLog = themed(styled.div`
  position: relative;
  width: auto;
  display: flex;
  flex: 1;
  overflow: auto;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-left: none;
  border-bottom: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};
`);

export const FlowNodeInstanceSkeleton = themed(styled(FlowNodeInstanceLog)`
  overflow: hidden;
`);
