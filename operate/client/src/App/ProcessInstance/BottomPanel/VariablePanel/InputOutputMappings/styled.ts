/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage as BaseEmptyMessage} from 'modules/components/EmptyMessage';

type ContentProps = {
  $isInfoBannerVisible: boolean;
};

const Content = styled.div<ContentProps>`
  ${({$isInfoBannerVisible}) => {
    return css`
      height: 100%;
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

const EmptyMessage = styled(BaseEmptyMessage)`
  align-self: center;
  margin: auto;
`;

export {Content, EmptyMessage};
