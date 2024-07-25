/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {NavItem} from '../NavItem';

import './SubNav.scss';

export default function SubNav(props) {
  return (
    <ul role="navigation" className="SubNav">
      {props.children}
    </ul>
  );
}

SubNav.Item = function SubNavItem(props) {
  return (
    <li>
      <NavItem {...props} />
    </li>
  );
};
