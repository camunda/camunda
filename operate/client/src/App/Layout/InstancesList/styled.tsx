/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';

type ContainerProps = {
  $hasLeftPanel?: boolean;
  $hasFooter?: boolean;
  $hasAdditionalTopContent?: boolean;
};

const gridColumnLayout = css<ContainerProps>`
  ${({$hasLeftPanel = false}) => {
    if ($hasLeftPanel) {
      return css`
        grid-template-columns: auto minmax(0, 1fr);
      `;
    }

    return css`
      grid-template-columns: 1fr;
    `;
  }}
`;

const gridRowLayout = css<ContainerProps>`
  ${({$hasFooter = false, $hasAdditionalTopContent = false}) => {
    if (!$hasAdditionalTopContent && !$hasFooter) {
      return css`
        grid-template-rows: 1fr;
      `;
    }

    if (!$hasFooter) {
      return css`
        grid-template-rows: auto 1fr;
      `;
    }

    if (!$hasAdditionalTopContent) {
      return css`
        grid-template-rows: 1fr var(--cds-spacing-09);
      `;
    }

    return css`
      grid-template-rows: auto 1fr var(--cds-spacing-09);
    `;
  }}
`;

const Container = styled.div<ContainerProps>`
  display: grid;
  height: 100%;
  position: relative;
  overflow: auto;

  ${gridColumnLayout}
  ${gridRowLayout}
`;

const PanelContainer = styled.div`
  overflow: auto;
`;

export {Container, PanelContainer};
