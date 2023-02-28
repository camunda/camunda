/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.section`
  ${({theme}) =>
    css`
      padding: ${theme.spacing04} ${theme.spacing05};
      border-bottom: 1px solid var(--cds-border-subtle);
    `}
`;

export {Container};
