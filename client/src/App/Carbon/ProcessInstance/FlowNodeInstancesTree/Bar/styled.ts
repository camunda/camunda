/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {StateIcon as BaseStateIcon} from 'modules/components/Carbon/StateIcon';
import {INSTANCE_HISTORY_LEFT_PADDING} from 'modules/constants';

const NodeName = styled.span`
  margin-left: var(--cds-spacing-02);
`;

const Container = styled.div`
  display: flex;
  justify-content: space-between;
  flex: 1;
`;

const StateIcon = styled(BaseStateIcon)`
  position: absolute;
  left: ${INSTANCE_HISTORY_LEFT_PADDING};
  align-self: center;
`;

export {NodeName, Container, StateIcon};
