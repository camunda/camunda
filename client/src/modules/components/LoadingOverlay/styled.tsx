/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {keyframes} from 'styled-components';

const Overlay = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: ${({theme}) => theme.colors.overlay};
`;

const SpinnerKeyframes = keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`;

const Spinner = styled.div`
  border-radius: 50%;
  width: 20px;
  height: 20px;
  border: 3px solid ${({theme}) => theme.colors.ui06};
  border-right-color: transparent;
  animation: ${SpinnerKeyframes} 0.7s infinite linear;
`;

export {Overlay, Spinner};
