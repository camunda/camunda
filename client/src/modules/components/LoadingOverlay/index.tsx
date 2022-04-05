/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as React from 'react';
import styled from 'styled-components';

import {Overlay, Spinner} from './styled';

type Props = {
  'data-testid'?: string;
};

const LoadingOverlay = styled<React.FC<Props>>((props) => (
  <Overlay {...props}>
    <Spinner />
  </Overlay>
))``;

export {LoadingOverlay, Spinner};
