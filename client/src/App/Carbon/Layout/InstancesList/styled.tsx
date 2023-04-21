/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';

const Container = styled.div`
  ${() => {
    return css`
      display: grid;
      height: 100%;
      grid-template-columns: auto 1fr ${COLLAPSABLE_PANEL_MIN_WIDTH};
      position: relative;
    `;
  }}
`;

export {Container};
