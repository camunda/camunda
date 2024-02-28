/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import {zNotificationContainer} from 'modules/constants/componentHierarchy';
import styled from 'styled-components';

const Container = styled.div`
  position: absolute;
  top: ${rem(56)};
  z-index: ${zNotificationContainer};
  right: var(--cds-spacing-03);
`;

export {Container};
