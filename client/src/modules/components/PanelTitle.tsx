/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const PanelTitle = styled.h3`
  ${({theme}) => css`
    color: var(--cds-text-primary);
    ${theme.productiveHeading03};
  `}
`;

export {PanelTitle};
