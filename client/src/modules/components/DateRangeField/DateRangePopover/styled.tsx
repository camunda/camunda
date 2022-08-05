/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Popover as BasePopover} from 'modules/components/Popover';
import {zDateRangePopover} from 'modules/constants/componentHierarchy';

const Popover = styled(BasePopover)`
  padding: 16px;
  z-index: ${zDateRangePopover};
`;

const Footer = styled.div`
  display: flex;
  justify-content: flex-end;
  button:not(:first-child) {
    margin-left: 16px;
  }
`;

export {Popover, Footer};
