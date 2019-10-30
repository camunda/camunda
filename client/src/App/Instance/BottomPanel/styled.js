/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import SplitPane from 'modules/components/SplitPane';

const BorderColors = css`
  ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
`;

export const Pane = styled(SplitPane.Pane)`
  ${({expandState}) => expandState === 'DEFAULT' && 'height: 50%'}
`;

export const PaneBody = styled(SplitPane.Pane.Body)`
  flex-direction: row;

  /* defines the space children take */
  > * {
    width: 50%;
  }
`;

export const Headline = themed(styled.span`
  padding-right: 15px;
  position: relative;

  &:after {
    content: '';
    position: absolute;
    top: -5px;
    right: 0px;
    height: 30px;
    width: 1px;
    background: ${BorderColors};
  }
`);

export const Pills = styled.div`
  display: flex;
  align-items: center;
  margin-left: 15px;
`;

export const PaneHeader = styled(SplitPane.Pane.Header)`
  display: flex;
  align-items: center;
  border-bottom: none;
`;

export const Section = themed(styled.div`
  flex: 1;
  border: solid 1px ${BorderColors};
  border-top: none;
  border-bottom: none;
`);

export const PaneFooter = styled(SplitPane.Pane.Footer)`
  text-align: right;
`;
