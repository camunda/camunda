/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {StatusMessage} from 'modules/components/StatusMessage';

const HEADER_HEIGHT = 57;
const METRIC_PANEL_HEIGHT = 234;
const TILES_TOP_PADDING = 20;
const TILES_BOTTOM_PADDING = 10;

const Container = styled.main`
  ${({theme}) => {
    const colors = theme.colors.dashboard;

    return css`
      display: flex;
      flex-direction: column;
      background-color: ${colors.backgroundColor};
      height: calc(100vh - ${HEADER_HEIGHT}px);
    `;
  }}
`;

const TileWrapper = styled.div`
  display: flex;
  height: calc(
    100vh -
      ${HEADER_HEIGHT +
      TILES_TOP_PADDING +
      METRIC_PANEL_HEIGHT +
      TILES_BOTTOM_PADDING}px
  );
`;

const PanelStyles: ThemedInterpolationFunction = ({theme}) => {
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

      ${StatusMessage} {
        margin-top: 53px;
      }
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
    const opacity = theme.opacity.dashboard.tileTitle;

    return css`
      margin: 0 0 14px;
      padding: 0;

      font-family: IBM Plex Sans;
      font-size: 16px;
      font-weight: 600;
      line-height: 2;
      color: ${theme.colors.text02};
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

  ${StatusMessage} {
    margin-top: 207px;
  }
`;

const Footer = styled.div`
  ${({theme}) => {
    const colors = theme.colors.dashboard.footer;

    return css`
      background-color: ${colors.backgroundColor};
      padding: 12px 20px;
      text-align: right;
    `;
  }}
`;

const Tiles = styled.div`
  padding: ${TILES_TOP_PADDING}px 20px 10px;
`;

export {
  Container,
  TileWrapper,
  MetricPanelWrapper,
  Tile,
  TileTitle,
  TileContent,
  Footer,
  Tiles,
};
