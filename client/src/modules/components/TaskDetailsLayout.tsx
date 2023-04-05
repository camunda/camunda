/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {Layer} from '@carbon/react';

const ScrollableContent = styled(Layer)`
  width: 100%;
  height: 100%;
  overflow: auto;
`;

type TaskDetailsRowProps = {
  $disabledSidePadding?: boolean;
};

const TaskDetailsRow = styled.div<TaskDetailsRowProps>`
  ${({$disabledSidePadding = false}) => css`
    width: 100%;
    max-width: ${rem(900)};
    ${$disabledSidePadding
      ? undefined
      : css`
          padding: 0 var(--cds-spacing-05);
        `}
  `}
`;

const TaskDetailsContainer = styled.div`
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: minmax(max-content, calc(100% - ${rem(62)})) ${rem(62)};
  justify-items: center;
`;

export {TaskDetailsContainer, TaskDetailsRow, ScrollableContent};
