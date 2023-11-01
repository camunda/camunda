/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';

type ContainerProps = {
  $hasLeftPanel?: boolean;
  $hasRightPanel?: boolean;
  $hasFooter?: boolean;
};

const gridColumnLayout = css<ContainerProps>`
  ${({$hasLeftPanel = false, $hasRightPanel = false}) => {
    if ($hasLeftPanel && $hasRightPanel) {
      return css`
        grid-template-columns: auto minmax(0, 1fr) ${COLLAPSABLE_PANEL_MIN_WIDTH};
      `;
    }

    return css`
      grid-template-columns: 1fr;
    `;
  }}
`;

const gridRowLayout = css<ContainerProps>`
  ${({$hasFooter = false}) => {
    if ($hasFooter) {
      return css`
        grid-template-rows: 1fr var(--cds-spacing-09);
      `;
    }

    return css`
      grid-template-rows: 1fr;
    `;
  }}
`;

const Container = styled.div<ContainerProps>`
  display: grid;
  height: 100%;
  position: relative;

  ${gridColumnLayout}
  ${gridRowLayout}
`;

export {Container};
