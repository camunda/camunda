/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => css`
    width: 100%;
    height: 100%;
    display: grid;
    grid-template-rows: auto 1fr;
    row-gap: ${theme.spacing05};
    position: relative;
    padding-top: ${theme.spacing07};
  `}
`;

export {Container};
