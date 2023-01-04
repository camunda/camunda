/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const PanelHeader = styled.div`
  ${({theme}) => css`
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 ${theme.spacing05};
    min-height: ${rem(32)};
  `}
`;

export {PanelHeader};
