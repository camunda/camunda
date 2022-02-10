/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {CmIcon} from '@camunda-cloud/common-ui-react';
import Table from 'modules/components/Table';

const Name = styled(Table.TD)`
  display: flex;
  align-items: center;
`;

const State = styled(CmIcon)`
  margin-right: 11px;
`;

export {Name, State};
