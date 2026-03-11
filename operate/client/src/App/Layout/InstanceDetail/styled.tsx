/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';

const OuterContainer = styled.div`
  display: flex;
  height: 100%;
  overflow: hidden;
`;

const MainContent = styled.div`
  flex: 1;
  min-width: 0;
  overflow: hidden;
`;

type Props = {
  $hasFooter?: boolean;
  $hasBreadcrumb?: boolean;
};

const Container = styled.div<Props>`
  ${({$hasBreadcrumb = false, $hasFooter = false}) => {
    return css`
      display: grid;
      height: 100%;
      grid-template-rows: ${`
        ${
          $hasBreadcrumb ? 'var(--cds-spacing-07)' : ''
        } minmax(var(--cds-spacing-09), auto) 1fr ${$hasFooter ? 'var(--cds-spacing-09)' : ''}
      `};
    `;
  }}
`;

const PanelContainer = styled.div`
  overflow: hidden;
`;

const RightPanelContainer = styled.div`
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
  border-left: 1px solid var(--cds-border-subtle-01);
`;

export {OuterContainer, MainContent, Container, PanelContainer, RightPanelContainer};
