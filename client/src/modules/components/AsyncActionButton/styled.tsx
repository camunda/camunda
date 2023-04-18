/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {
  Button as BaseButton,
  InlineLoading as BaseInlineLoading,
} from '@carbon/react';

const InlineLoading = styled(BaseInlineLoading)`
  width: fit-content;
`;

const Button = styled(BaseButton)`
  &.hide {
    display: none;
  }
`;

export {Button, InlineLoading};
