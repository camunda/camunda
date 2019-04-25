/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children} from 'react';
import PropTypes from 'prop-types';
import {CSSTransition, TransitionGroup} from 'react-transition-group';

function Transition(props) {
  return (
    props.children && (
      <CSSTransition classNames="transition" {...props}>
        {Children.only(props.children)}
      </CSSTransition>
    )
  );
}

Transition.propTypes = {
  children: PropTypes.node.isRequired
};

export {Transition, TransitionGroup};
