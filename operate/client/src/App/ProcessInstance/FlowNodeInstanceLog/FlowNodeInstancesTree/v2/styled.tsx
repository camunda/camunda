/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';
import styled, {css} from 'styled-components';
import {ElementInstanceIcon as BaseElementInstanceIcon} from 'modules/components/ElementInstanceIcon';

const ElementInstanceIcon = styled(BaseElementInstanceIcon)<{
  $hasLeftMargin: boolean;
}>`
  ${({$hasLeftMargin}) => {
    return css`
      ${$hasLeftMargin
        ? css`
            margin-left: ${INSTANCE_HISTORY_LEFT_PADDING};
          `
        : ''}
    `;
  }}
`;

export {ElementInstanceIcon};
