/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';

type Props = {
  $hasFooter?: boolean;
  $hasBreadcrumb?: boolean;
  $hasRightOverlay?: boolean;
  $hasRightPanel?: boolean;
};

type PanelContainerProps = {
  $hasRightOverlay?: boolean;
};

const Container = styled.div<Props>`
  ${({
    $hasBreadcrumb = false,
    $hasFooter = false,
    $hasRightOverlay = false,
    $hasRightPanel = false,
  }) => {
    return css`
      display: grid;
      height: 100%;
      grid-template-rows: ${`
        ${
          $hasBreadcrumb ? 'var(--cds-spacing-07)' : ''
        } var(--cds-spacing-09) 1fr ${$hasFooter ? 'var(--cds-spacing-09)' : ''}
      `};

      ${$hasRightOverlay
        ? css`
            padding-right: 420px;
          `
        : $hasRightPanel
          ? css`
              padding-right: ${COLLAPSABLE_PANEL_MIN_WIDTH};
            `
          : ''}
    `;
  }}
`;

const PanelContainer = styled.div<PanelContainerProps>`
  overflow: auto;
`;

export {Container, PanelContainer};
