/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/EmptyMessage';
import {ActionableNotification} from '@carbon/react';

type ContentProps = {
  $isInfoBannerVisible: boolean;
};

const Content = styled.div<ContentProps>`
  ${({$isInfoBannerVisible}) => {
    return css`
      height: 100%;
      padding-inline: var(--cds-spacing-05);
      overflow: hidden;
      display: grid;
      grid-template-rows: ${$isInfoBannerVisible ? 'auto 1fr' : '1fr'};
      ${$isInfoBannerVisible &&
      css`
        grid-gap: var(--cds-spacing-05);
      `}
    `;
  }}
`;

const FullSizeActionableNotification = styled(ActionableNotification)`
  max-inline-size: unset;

  .cds--actionable-notification__text-wrapper {
    max-inline-size: 80ch; /* Keep text at a readable length */
  }
`;

const EmptyMessage = styled(BaseEmptyMessage)`
  align-self: center;
  margin: auto;
`;

export {Content, FullSizeActionableNotification, EmptyMessage};
