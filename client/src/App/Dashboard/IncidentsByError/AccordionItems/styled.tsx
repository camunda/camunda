/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import BaseInstancesBar from 'modules/components/InstancesBar';
import {InstancesBarStyles} from '../styled';

const Ul = styled.ul`
  margin-top: 8px;
  margin-bottom: 16px;
`;

const Li = styled.li`
  margin: 6px 0 0;
  padding: 0;
`;

const InstancesBar = styled(BaseInstancesBar)`
  ${InstancesBarStyles}
`;

export {Ul, Li, InstancesBar};
