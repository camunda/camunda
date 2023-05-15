/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type Props = {
  $hasModificationHeader?: boolean;
  $hasBreadcrumb?: boolean;
};

const Container = styled.div<Props>`
  ${({$hasModificationHeader = false, $hasBreadcrumb = false}) => {
    return css`
      display: grid;
      height: 100%;
      grid-template-rows: ${`${
        $hasModificationHeader ? 'var(--cds-spacing-07)' : ''
      }
        ${
          $hasBreadcrumb ? 'var(--cds-spacing-07)' : ''
        } var(--cds-spacing-09) 1fr
      `};
    `;
  }}
`;

export {Container};
