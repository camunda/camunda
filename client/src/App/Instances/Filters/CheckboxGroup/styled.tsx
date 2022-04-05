/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CmCheckboxGroup, CmCheckbox} from '@camunda-cloud/common-ui-react';
import styled from 'styled-components';

const Container = styled.div`
  padding-bottom: 14px;
`;

const Group = styled(CmCheckboxGroup)`
  padding-left: 24px;
`;

const GroupCheckbox = styled(CmCheckbox)`
  padding-bottom: 14px;
`;

const Checkbox = styled(CmCheckbox)`
  padding-left: 2px;
  padding-bottom: 3px;
`;

export {Container, Group, GroupCheckbox, Checkbox};
