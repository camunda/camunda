/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import EmptyPanel from 'modules/components/EmptyPanel';

const HEADER_HEIGHT = 56;
const METRIC_PANEL_HEIGHT = 234;

const Dashboard = styled.main`
  padding: 0 20px 0;
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

const TileWrapper = styled.div`
  display: flex;
  height: calc(100vh - ${HEADER_HEIGHT + METRIC_PANEL_HEIGHT}px);
`;

const PanelStyles = ({theme}: any) => {
  const colors = theme.colors.dashboard.panelStyles;
  const shadow = theme.shadows.dashboard.panelStyles;

  return css`
    border-radius: 3px;
    border: solid 1px ${colors.borderColor};
    background-color: ${colors.backgroundColor};
    box-shadow: ${shadow};
  `;
};

const MetricPanelWrapper = styled.div`
  ${({theme}) => {
    const colors = theme.colors.dashboard.metricPanelWrapper;

    return css`
      ${PanelStyles};
      color: ${colors.color};
      height: 198px;
    `;
  }}
`;

const Tile = styled.div`
  ${PanelStyles}

  /* flex-parent */
  display: flex;
  flex-direction: column;
  overflow: auto;

  /* flex-child */
  margin: 8px 8px 0 0;
  padding: 24px 24px 40px 28px;
  width: 50%;

  &:last-child {
    margin-right: 0;
  }
`;

const TileTitle = styled.h2`
  ${({theme}) => {
    const colors = theme.colors.dashboard.tileTitle;
    const opacity = theme.opacity.dashboard.tileTitle;

    return css`
      margin: 0 0 14px;
      padding: 0;

      font-family: IBM Plex Sans;
      font-size: 16px;
      font-weight: 600;
      line-height: 2;
      color: ${colors.color};
      opacity: ${opacity};
    `;
  }}
`;

const TileContent = styled.div`
  position: relative;
  overflow-y: scroll;
  flex: 1;

  /* these styles are required to fully display focus borders */
  padding-left: 4px;
  margin-left: -4px;
  padding-top: 4px;
  margin-top: -4px;
`;

const EmptyMetricPanel = styled(EmptyPanel)`
  padding-top: 26px;
`;

const EmptyMetricPanelWrapper = styled.div`
  padding: 22px 24px;
  display: flex;
  height: 100%;
`;

const MultiRowContainer = styled.div`
  display: flex;
  flex-direction: column;
`;

const Block = styled.div`
  ${({theme}) => {
    const colors = theme.colors.dashboard.block;
    const opacity = theme.opacity.dashboard.block;

    return css`
      margin: 15px 19px 19px 33px;
      height: 21px;
      flex-grow: 1;
      background: ${colors.backgroundColor};
      opacity: ${opacity};
    `;
  }}
`;

const Footer = styled.div`
  padding: 12px 0;
  text-align: right;
`;

export {
  Dashboard,
  TileWrapper,
  MetricPanelWrapper,
  Tile,
  TileTitle,
  TileContent,
  EmptyMetricPanel,
  EmptyMetricPanelWrapper,
  MultiRowContainer,
  Block,
  Footer,
};
