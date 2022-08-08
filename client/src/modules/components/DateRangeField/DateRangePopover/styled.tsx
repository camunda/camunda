/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {Popover as BasePopover} from 'modules/components/Popover';
import {zDateRangePopover} from 'modules/constants/componentHierarchy';

const Popover = styled(BasePopover)`
  z-index: ${zDateRangePopover};
`;

const FieldContainer = styled.div`
  display: flex;
  padding: 16px;
  align-items: flex-end;
  border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
`;

const Dash = styled.span`
  ${styles.label02};
  margin: 0 8px 6px 8px;
`;

const Footer = styled.div`
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  button:not(:first-child) {
    margin-left: 16px;
  }
`;

export {Popover, Footer, Dash, FieldContainer};
