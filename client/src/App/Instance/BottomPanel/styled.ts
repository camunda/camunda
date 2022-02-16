/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import SplitPane from 'modules/components/SplitPane';

const Pane = styled(SplitPane.Pane)`
  ${({expandState}) => {
    return css`
      ${expandState === 'DEFAULT'
        ? css`
            height: 50%;
          `
        : ''}
    `;
  }}
`;

const PaneBody = styled(SplitPane.Pane.Body)`
  flex-direction: row;
  /* defines the space children take */
  > * {
    width: 50%;
  }
`;

const Headline = styled.span`
  ${({theme}) => {
    const colors = theme.colors.bottomPanel;

    return css`
      padding-right: 15px;
      position: relative;

      &:after {
        content: '';
        position: absolute;
        top: -5px;
        right: 0px;
        height: 30px;
        width: 1px;
        background: ${colors.borderColor};
      }
    `;
  }}
`;

const Pills = styled.div`
  display: flex;
  align-items: center;
  margin-left: 15px;
`;

const PaneHeader = styled(SplitPane.Pane.Header)`
  display: flex;
  align-items: center;
  padding: 9px 10px 9px 20px;

  ${({expandState}) => {
    return css`
      ${expandState === 'COLLAPSED' &&
      css`
        border-bottom: none;
      `}
    `;
  }}
`;

const Section = styled.div`
  ${({theme}) => {
    const colors = theme.colors.bottomPanel;

    return css`
      flex: 1;
      border: solid 1px ${colors.borderColor};
      border-top: none;
      border-bottom: none;
    `;
  }}
`;

const PaneFooter = styled(SplitPane.Pane.Footer)`
  text-align: right;
`;

export {Pane, PaneBody, Headline, Pills, PaneHeader, Section, PaneFooter};
