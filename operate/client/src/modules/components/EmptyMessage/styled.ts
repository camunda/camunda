/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';

const Stack = styled(BaseStack)`
  max-width: 360px;
`;

const Message = styled.p`
  ${styles.heading02};
  margin: 0;
`;

const AdditionalInfo = styled.p`
  ${styles.bodyShort01};
  margin: 0;
`;

export {Message, AdditionalInfo, Stack};
