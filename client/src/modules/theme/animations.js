/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {keyframes} from 'styled-components';

const Animations = {
  Spinner: keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`,
  Selection: keyframes`
  0% {
    opacity: 0.3;
  }
  100% {
    opacity: 1;
  }
`,
  fold: (minValue, maxValue) => keyframes`
  0% {
    max-height: ${minValue.toString() + 'px'};
  }
  100% {
    max-height: ${maxValue.toString() + 'px'}
  }`
};

export default Animations;
