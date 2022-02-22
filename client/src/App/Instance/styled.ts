/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import SplitPane from 'modules/components/SplitPane';

const Instance = styled.main`
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
`;

const PaneBody = styled(SplitPane.Pane.Body)`
  flex-direction: row;
`;

const Section = styled.div`
  ${({theme}) => {
    const colors = theme.colors.instance.section;

    return css`
      flex: 1;
      border: solid 1px ${colors.borderColor};
      border-top: none;
      border-bottom: none;
    `;
  }}
`;

const FlowNodeInstanceLog = styled.div`
  ${({theme}) => {
    const colors = theme.colors.instance.flowNodeInstanceLog;

    return css`
      position: relative;
      width: auto;
      display: flex;
      flex: 1;
      overflow: hidden;
      border: solid 1px ${colors.borderColor};
      border-top: none;
      border-left: none;
      border-bottom: none;
      color: ${colors.color};
    `;
  }}
`;

const NodeContainer = styled.div`
  ${({theme}) => {
    return css`
      background-image: url(${theme.images.zeebraStripe});
      position: absolute;
      width: inherit;
      min-width: 100%;
      min-height: min-content;
      margin: 0;
      padding: 0;
      padding-left: 8px;
    `;
  }}
`;

const pseudoBorder: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.instance.pseudoBorder;

  return css`
    /* Border with individual z-index to be layered above child */
    &:before {
      position: absolute;
      content: '';
      height: 1px;
      width: 100%;
      z-index: 5;
      top: 0px;
      left: 0px;
      border-top: solid 1px ${colors.borderColor};
    }
  `;
};

const SplitPaneTop = styled(SplitPane.Pane)`
  ${({theme}) => {
    const colors = theme.colors.instance.splitPaneTop;

    return css`
      border-top: none;
      background-color: ${colors.backgroundColor};
      ${pseudoBorder}
    `;
  }}
`;

const SplitPaneBody = styled(SplitPane.Pane.Body)`
  position: relative;
  border: none;
  ${pseudoBorder}
`;

export {
  Instance,
  PaneBody,
  Section,
  FlowNodeInstanceLog,
  NodeContainer,
  SplitPaneTop,
  SplitPaneBody,
};
