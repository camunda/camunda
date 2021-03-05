/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {memo} from 'react';
import Badge from 'modules/components/Badge';
import * as Styled from './styled';
import {Link} from 'react-router-dom';

type BrandNavElementProps = {
  to: React.ComponentProps<typeof Link>['to'];
  dataTest: string;
  title: string;
  label: string;
};

export const BrandNavElement: React.FC<BrandNavElementProps> = memo((props) => (
  <Styled.ListItem>
    <Styled.Brand
      to={props.to}
      title={props.title}
      data-testid={props.dataTest}
    >
      <Styled.LogoIcon />
      <Styled.BrandLabel>{props.label}</Styled.BrandLabel>
    </Styled.Brand>
  </Styled.ListItem>
));

type LinkElementProps = {
  to: React.ComponentProps<typeof Link>['to'];
  dataTest: string;
  title: string;
  isActive: boolean;
  label: string;
};

export const LinkElement: React.FC<LinkElementProps> = memo((props) => (
  <Styled.ListItem aria-label={props.label}>
    <Styled.DashboardLink
      to={props.to}
      title={props.title}
      data-testid={props.dataTest}
    >
      <Styled.DashboardLabel
        $isActive={props.isActive}
        data-testid="dashboard-label"
      >
        {props.label}
      </Styled.DashboardLabel>
    </Styled.DashboardLink>
  </Styled.ListItem>
));

type NavElementProps = {
  to: React.ComponentProps<typeof Link>['to'];
  dataTest: string;
  title: string;
  isActive: boolean;
  linkProps: unknown;
  type?: 'RUNNING_INSTANCES' | 'FILTERS' | 'INCIDENTS' | 'SELECTIONS';
  label: string;
  count: number | string;
  className?: string;
};

export const NavElement: React.FC<NavElementProps> = memo((props) => (
  <Styled.ListItem className={props.className} aria-label={props.label}>
    <Styled.ListLink
      title={props.title}
      data-testid={props.dataTest}
      to={props.to}
      {...props.linkProps}
    >
      <Styled.NavigationLabel $isActive={props.isActive}>
        {props.label}
      </Styled.NavigationLabel>
      <Badge $isActive={props.isActive} type={props.type}>
        {props.count}
      </Badge>
    </Styled.ListLink>
  </Styled.ListItem>
));
