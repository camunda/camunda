/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {CmButton} from '@camunda-cloud/common-ui-react';
import {WarningMessage} from 'modules/components/Messages/WarningMessage';

const WarningContainer = styled(WarningMessage)`
  display: flex;
  margin: 24px 0;
`;

const DeleteButton = styled(CmButton)`
  margin-left: 15px;
  width: 117px;
`;

const Description = styled.p`
  margin: 10px 0 24px 0;
`;

export {WarningContainer, DeleteButton, Description};
