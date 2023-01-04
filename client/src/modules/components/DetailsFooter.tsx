/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const DetailsFooter = styled.div`
  ${({theme}) => css`
    text-align: right;
    padding: ${theme.spacing03} ${theme.spacing05};
    background-color: var(--cds-background);
    border-top: 1px solid var(--cds-border-subtle);
    display: flex;
    align-items: center;
    justify-content: end;
    min-height: ${rem(62)};
  `}
`;

export {DetailsFooter};
