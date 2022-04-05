/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {CollapsablePanel as CollapsablePanelBase} from 'modules/components/CollapsablePanel';

const CollapsablePanel = styled(CollapsablePanelBase)`
  ${({theme}) =>
    css`
      border-right: 1px solid ${theme.colors.borderColor};
    `}
`;

export {CollapsablePanel};
