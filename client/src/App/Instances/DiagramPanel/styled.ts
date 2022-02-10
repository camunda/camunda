/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {default as SplitPaneComponent} from 'modules/components/SplitPane';

const EmptyMessageWrapper = styled.div`
  position: relative;
`;

const DiagramEmptyMessage = styled(EmptyMessage)`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

const PaneHeader = styled(SplitPaneComponent.Pane.Header)`
  border-radius: inherit;
`;

const PaneBody = styled(SplitPaneComponent.Pane.Body)`
  ${({theme}) => {
    const colors = theme.colors.modules.panel;

    return css`
      position: relative;
      border-bottom: 1px solid ${colors.borderColor};
    `;
  }}
`;

export {EmptyMessageWrapper, DiagramEmptyMessage, PaneHeader, PaneBody};
