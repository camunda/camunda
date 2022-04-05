/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {ReactComponent as Warning} from 'modules/icons/warning.svg';

const Container = styled.span`
  padding: 4px 5px 2px;
`;

const WarningIcon = styled(Warning)`
  width: 16px;
`;

export {Container, WarningIcon};
