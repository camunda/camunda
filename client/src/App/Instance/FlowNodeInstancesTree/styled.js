/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

import StateIcon from 'modules/components/StateIcon';

export const NodeStateIcon = styled(
  withStrippedProps(['indentationMultiplier'])(StateIcon)
)`
  top: 6px;
  left: ${({indentationMultiplier}) =>
    indentationMultiplier ? -indentationMultiplier * 32 + 'px' : '5px'};
`;

const connectionDotStyles = css`
  height: 5px;
  width: 5px;
  border-radius: 50%;
  background: ${themeStyle({
    dark: '#65666D',
    light: Colors.uiLight05,
  })};
`;

const connectionLineStyles = css`
  &:before {
    content: '';
    position: absolute;
    /* line ends 10px above the bottom of the element */
    height: calc(100% - 10px);
    width: 1px;
    left: -17px;
    background: ${themeStyle({
      dark: '#65666D',
      light: Colors.uiLight05,
    })};
  }

  /* show a final dot at the end of each connection line */
  &:after {
    content: '';
    position: absolute;
    bottom: 9px;
    left: -19px;
    ${connectionDotStyles};
  }
`;

export const NodeDetails = themed(styled.div`
  display: flex;
  align-items: center;
  position: absolute;
  color: ${({theme, isSelected}) =>
    isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : 'rgba(69, 70, 78, 0.9)'};

  &:before {
    content: '';
    position: absolute;
    left: -51px;
    top: 13px;
    ${({showConnectionDot}) => showConnectionDot && connectionDotStyles};
  }
`);

export const Ul = themed(styled.ul`
  position: relative;
  ${({showConnectionLine}) => showConnectionLine && connectionLineStyles};
`);

export const Li = themed(styled.li`
  margin-left: 32px;

  /* adjust focus position for first tree elements */
  &:first-child > div:nth-child(2) > button {
    ${({treeDepth}) =>
      treeDepth === 1 &&
      css`
        height: calc(100% - 5px);
        top: 4px;
      `};
  }

  /* don't show work flode nodes top border */
  &:first-child > div:nth-child(2) > div > div {
    border-top-width: ${({treeDepth}) => treeDepth === 1 && '0px'};
  }
`);
