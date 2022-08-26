/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {CmIconButton} from '@camunda-cloud/common-ui-react';
import {IconButton} from 'modules/components/IconButton';

const Container = styled.div`
  display: flex;
  align-items: center;
`;

const DiffEditorButton = styled(CmIconButton)`
  margin-left: 6px;
`;

const ModalIconButton = styled(IconButton)`
  svg {
    margin-top: 4px;
  }
`;

export {Container, DiffEditorButton, ModalIconButton};
