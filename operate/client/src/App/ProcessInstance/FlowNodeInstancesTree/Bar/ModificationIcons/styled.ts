/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {WarningAltFilled, Error, Add} from '@carbon/react/icons';

const Container = styled.div`
  display: flex;
  align-items: center;
  justify-self: flex-end;
  margin: 0 var(--cds-spacing-05);
`;

const iconStyles = css`
  color: var(--cds-icon-disabled);
`;

const AddIcon = styled(Add)`
  ${iconStyles}
`;

const CancelIcon = styled(Error)`
  ${iconStyles}
`;

const WarningIcon = styled(WarningAltFilled)`
  color: var(--cds-support-warning);
`;

export {Container, AddIcon, CancelIcon, WarningIcon};
