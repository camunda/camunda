/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => css`
    position: absolute;
    top: ${rem(56)};
    z-index: 1000;
    right: ${theme.spacing03};
  `}
`;

export {Container};
