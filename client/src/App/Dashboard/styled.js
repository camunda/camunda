/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import EmptyPanel from 'modules/components/EmptyPanel';

const HEADER_HEIGHT = 56;
const METRIC_PANEL_HEIGHT = 234;

export const Dashboard = styled.main`
  padding: 0 20px 0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

export const TitleWrapper = styled.div`
  display: flex;
  height: calc(100vh - ${HEADER_HEIGHT + METRIC_PANEL_HEIGHT}px);
`;

const PanelStyles = css`
  border-radius: 3px;
  border: solid 1px
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  box-shadow: ${themeStyle({
    dark: '0 3px 6px 0 #000000',
    light: '0 2px 3px 0 rgba(0, 0, 0, 0.1)'
  })};
`;

export const MetricPanelWrapper = themed(styled.div`
  ${PanelStyles};

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06
  })};

  height: 198px;
`);

export const Tile = themed(styled.div`
  ${PanelStyles}

  // flex-parent
  display: flex;
  flex-direction: column;
  overflow: auto;

  // flex-child
  margin: 8px 8px 0 0;
  padding: 24px 24px 40px 28px;
  width: 50%;

  &:last-child {
    margin-right: 0;
  }
`);

export const TileTitle = themed(styled.h2`
  margin: 0 0 14px;
  padding: 0;

  font-family: IBMPlexSans;
  font-size: 16px;
  font-weight: 600;
  line-height: 2;
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};
`);

export const TileContent = styled.div`
  position: relative;
  overflow-y: auto;
  flex: 1;

  // these styles are required to fully display focus borders
  padding-left: 4px;
  margin-left: -4px;
  padding-top: 4px;
  margin-top: -4px;
`;

export const EmptyMetricPanel = styled(EmptyPanel)`
  padding-top: 26px;
`;

export const EmptyMetricPanelWrapper = styled.div`
  padding: 22px 24px;
  display: flex;
  height: 100%;
`;

export const Footer = styled.div`
  padding: 12px 0;
  text-align: right;
`;
