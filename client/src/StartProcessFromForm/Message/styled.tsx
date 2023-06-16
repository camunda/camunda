/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PoweredBy as BasePoweredBy} from 'modules/components/PoweredBy';
import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-06);
`;

const PoweredBy = styled(BasePoweredBy)`
  align-self: flex-end;
  padding-right: var(--cds-spacing-06);
`;

export {Container, PoweredBy};
