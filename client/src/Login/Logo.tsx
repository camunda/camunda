/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {ReactComponent} from 'modules/icons/logo.svg';

const Logo = styled(ReactComponent)`
  fill: ${({theme}) => theme.colors.ui06};
`;

export {Logo};
