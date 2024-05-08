/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {Children} from 'react';
import {CSSTransition, TransitionGroup} from 'react-transition-group';

type Props = {
  children: React.ReactNode;
} & React.ComponentProps<typeof CSSTransition>;

const Transition: React.FC<Props> = (props) => {
  return (
    <CSSTransition classNames="transition" {...props}>
      {Children.only(props.children)}
    </CSSTransition>
  );
};

export {Transition, TransitionGroup};
