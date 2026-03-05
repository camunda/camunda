/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Layer as BaseLayer} from '@carbon/react';

const FooterContainer = styled.div`
  margin-top: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
`;

type VariableContainerProps = {
  $hasPendingVariable?: boolean;
};

const Layer = styled(BaseLayer)<VariableContainerProps>`
  ${({$hasPendingVariable}) => {
    return css`
      width: 100%;
      padding: var(--cds-spacing-02) var(--cds-spacing-07) var(--cds-spacing-02)
        var(--cds-spacing-05);
      display: grid;
      grid-template-columns: 1fr 2fr auto;
      grid-gap: var(--cds-spacing-05);
      ${$hasPendingVariable &&
      css`
        background-color: var(--cds-layer);
        align-items: center;
      `}
    `;
  }};
`;

export {FooterContainer, Layer};
