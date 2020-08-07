/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {memo} from 'react';
import PropTypes from 'prop-types';
import Badge from 'modules/components/Badge';
import * as Styled from './styled.js';

// export default React.memo(function Skeleton(props) {
export const BrandNavElement = memo((props) => (
  <Styled.ListItem>
    <Styled.Brand to={props.to} title={props.title} data-test={props.dataTest}>
      <Styled.LogoIcon />
      <Styled.BrandLabel>{props.label}</Styled.BrandLabel>
    </Styled.Brand>
  </Styled.ListItem>
));

BrandNavElement.propTypes = {
  to: PropTypes.string,
  dataTest: PropTypes.string,
  title: PropTypes.string,
  label: PropTypes.string,
};

export const LinkElement = memo((props) => (
  <Styled.ListItem aria-label={props.label}>
    <Styled.DashboardLink
      to={props.to}
      isActive={props.isActive}
      title={props.title}
      data-test={props.dataTest}
    >
      <Styled.DashboardLabel
        isActive={props.isActive}
        data-test="dashboard-label"
      >
        {props.label}
      </Styled.DashboardLabel>
    </Styled.DashboardLink>
  </Styled.ListItem>
));

LinkElement.propTypes = {
  to: PropTypes.string,
  dataTest: PropTypes.string,
  title: PropTypes.string,
  isActive: PropTypes.bool,
  label: PropTypes.string,
};

export const NavElement = memo((props) => (
  <Styled.ListItem className={props.className} aria-label={props.label}>
    <Styled.ListLink
      isActive={props.isActive}
      title={props.title}
      data-test={props.dataTest}
      {...props.linkProps}
    >
      <Styled.NavigationLabel isActive={props.isActive}>
        {props.label}
      </Styled.NavigationLabel>
      <Badge isActive={props.isActive} type={props.type}>
        {props.count}
      </Badge>
    </Styled.ListLink>
  </Styled.ListItem>
));

NavElement.propTypes = {
  dataTest: PropTypes.string,
  title: PropTypes.string,
  isActive: PropTypes.bool,
  linkProps: PropTypes.object,
  type: PropTypes.string,
  label: PropTypes.string,
  count: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  className: PropTypes.string,
};
