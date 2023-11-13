/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ListItem as BaseListItem, Modal as BaseModal} from '@carbon/react';

const ListItem = styled(BaseListItem)`
  list-style: decimal;
`;

const Modal = styled(BaseModal)`
  p {
    margin-bottom: 0;
  }
`;

export {ListItem, Modal};
