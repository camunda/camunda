/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Container, Link, Label} from './styled';
import {NavLink as BaseLink} from 'react-router-dom';

type NavElementProps = {
  to: React.ComponentProps<typeof BaseLink>['to'];
  icon?: React.ReactNode;
  title: string;
  label: string;
};

const NavElement: React.FC<NavElementProps> = ({title, to, icon, label}) => (
  <Container>
    <Link exact activeClassName="active" title={title} to={to}>
      {icon}
      <Label>{label}</Label>
    </Link>
  </Container>
);

export {NavElement};
