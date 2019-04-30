/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {keyframes} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';

const foldKeyFrames = (minValue, maxValue) => keyframes`
0% {
  max-height: ${minValue.toString() + 'px'};
}
100% {
  max-height: ${maxValue.toString() + 'px'}
}`;

const Animations = {
  SelectionTransition: timeout => styled(TransitionComponent)`
    &.transition-enter {
      opacity: 0;
    }
    &.transition-enter-active {
      opacity: 1;
      transition: opacity ${({timeout}) => timeout.enter + 'ms'};
      overflow: hidden;
      animation-name: ${foldKeyFrames(0, 474)};
      animation-duration: ${({timeout}) => timeout.enter + 'ms'};
    }

    &.transition-exit {
      opacity: 0;
      transition: opacity ${({timeout}) => timeout.exit + 'ms'};
    }
    &.transition-exit-active {
      opacity: 0;
      max-height: 0px;
      overflow: hidden;
      animation-name: ${foldKeyFrames(474, 0)};
      animation-duration: ${({timeout}) => timeout.exit + 'ms'};
    }
  `
};

export default Animations;
