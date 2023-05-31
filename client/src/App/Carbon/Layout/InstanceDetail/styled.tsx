/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type Props = {
  $hasFrameHeader?: boolean;
  $hasFrameFooter?: boolean;
  $hasBreadcrumb?: boolean;
};

const Container = styled.div<Props>`
  ${({
    $hasFrameHeader = false,
    $hasBreadcrumb = false,
    $hasFrameFooter = false,
  }) => {
    return css`
      display: grid;
      height: 100%;
      grid-template-rows: ${`${$hasFrameHeader ? 'var(--cds-spacing-07)' : ''}
        ${
          $hasBreadcrumb ? 'var(--cds-spacing-07)' : ''
        } var(--cds-spacing-09) 1fr ${
        $hasFrameFooter ? 'var(--cds-spacing-09)' : ''
      }
      `};

      ${$hasFrameHeader &&
      css`
        border: 4px solid var(--cds-interactive);
        border-top: none;
      `}
    `;
  }}
`;

export {Container};
