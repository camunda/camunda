/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Toggle as BaseToggle} from '@carbon/react';
import {styled} from 'styled-components';

const VariableValueContainer = styled.div`
  position: relative;
`;

const Toggle = styled(BaseToggle)`
  position: absolute;
  top: 0;
  right: 0;
`;

export {VariableValueContainer, Toggle};
