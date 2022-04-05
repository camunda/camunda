/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {CmNotificationContainer} from '@camunda-cloud/common-ui-react';

const NotificationContainer = styled(CmNotificationContainer)`
  position: absolute;
  bottom: 13px;
  left: 10px;
  z-index: 1;
`;

export {NotificationContainer};
