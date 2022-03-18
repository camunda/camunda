/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

const Container = styled.main`
  height: 100%;
  position: relative;
  overflow: hidden;
`;

const Content = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: calc(100% - ${COLLAPSABLE_PANEL_MIN_WIDTH});
`;

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel;

    return css`
      width: 1px;
      background-color: ${colors.borderColor};
    `;
  }}
`;

const RightContainer = styled.div`
  width: 100%;
  display: flex;
  flex-direction: column;
`;

export {Container, Content, Separator, RightContainer};
