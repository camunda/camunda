/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';

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
        } var(--cds-spacing-09) 1fr ${$hasFooter ? 'var(--cds-spacing-09)' : ''}
      `};
    `;
  }}
`;

const PanelContainer = styled.div`
  overflow: auto;
`;

export {Container, PanelContainer};
