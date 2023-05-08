/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {TextInput as BaseTextInput} from '@carbon/react';

const Container = styled.div`
  position: relative;

  svg {
    position: absolute;
    bottom: var(--cds-spacing-03);
    right: var(--cds-spacing-03);
  }
`;

const TextInput = styled(BaseTextInput)`
  input {
    padding-right: var(--cds-spacing-07);
  }
`;

export {Container, TextInput};
