/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const ClaimButtonContainer = styled.span`
  ${({theme}) => css`
    flex-shrink: 0;
    margin-left: ${theme.spacing05};
    display: flex;
    align-items: center;
  `}
`;

const Assignee = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-width: ${rem(163)};
`;

const AssigneeText = styled.span`
  width: max-content;
`;

const HelperText = styled.span`
  color: var(--cds-text-helper);
`;

export {Assignee, ClaimButtonContainer, HelperText, AssigneeText};
