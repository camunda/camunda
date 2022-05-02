/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {CmText} from '@camunda-cloud/common-ui-react';
import {TextField} from 'modules/components/TextField';

const VariableHeader = styled(CmText)`
  display: block;
  padding-bottom: 5px;
`;

const VariableNameField = styled(TextField)`
  padding-bottom: 5px;
`;

const VariableValueField = styled(TextField)`
  padding-bottom: 21px;
`;

export {VariableHeader, VariableValueField, VariableNameField};
