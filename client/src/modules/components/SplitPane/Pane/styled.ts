/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Panel} from 'modules/components/Panel';
import CollapseButton from 'modules/components/CollapseButton';
import {EXPAND_STATE} from 'modules/constants';

type PaneProps = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const Pane = styled(Panel)<PaneProps>`
  ${({expandState}) => {
    if (expandState !== EXPAND_STATE.COLLAPSED) {
      return css`
        flex-grow: 1;
      `;
    }
  }}
`;

const Body = Panel.Body;

const Footer = Panel.Footer;

const PaneCollapseButton = styled(CollapseButton)`
  margin: 0;
  margin-top: 3px;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

const ButtonsContainer = styled.div`
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
`;

export {Pane, Body, Footer, PaneCollapseButton, ButtonsContainer};
