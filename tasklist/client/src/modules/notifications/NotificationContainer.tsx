/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => css`
    position: absolute;
    top: ${rem(56)};
    z-index: 1000;
    right: ${theme.spacing03};
  `}
`;

export {Container};
