/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {Children, useRef} from 'react';
import {CSSTransition} from 'react-transition-group';

type Props = {
  children: React.ReactElement<React.RefAttributes<HTMLElement>>;
} & React.ComponentProps<typeof CSSTransition>;

const Transition: React.FC<Props> = ({children, ...props}) => {
  const nodeRef = useRef<HTMLDivElement | null>(null);

  return (
    <CSSTransition classNames="transition" nodeRef={nodeRef} {...props}>
      {React.cloneElement(Children.only(children), {
        ref: nodeRef,
      })}
    </CSSTransition>
  );
};

export {Transition};
