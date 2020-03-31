/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import Spinner from 'modules/components/Spinner';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Skeleton = themed(styled.div`
  background-color: ${themeStyle({
    dark: 'rgba(0, 0, 0, 0.65)',
    light: 'rgba(255, 255, 255, 0.75)',
  })};

  z-index: 2;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  position: absolute;
`);

export const SkeletonSpinner = themed(styled(Spinner)`
  position: absolute;
  top: 40.7%;
  height: 30px;
  width: 30px;
  border: 4px solid ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
  border-right-color: transparent;
`);
