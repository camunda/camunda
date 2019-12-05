/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {memo} from 'react';
import PropTypes from 'prop-types';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import * as Styled from '../styled.js';

// export default React.memo(function Skeleton(props) {
export const BrandNavElement = memo(props => (
  <li data-test={props.dataTest}>
    <Styled.Brand to={props.to} title={props.title}>
      <Styled.LogoIcon />
      <span>{props.label}</span>
    </Styled.Brand>
  </li>
));

BrandNavElement.propTypes = {
  to: PropTypes.string,
  dataTest: PropTypes.string,
  title: PropTypes.string,
  label: PropTypes.string
};

export const LinkElement = memo(props => (
  <li data-test={props.dataTest}>
    <Styled.DashboardLink
      to={props.to}
      isActive={props.isActive}
      title={props.title}
    >
      <span>{props.label}</span>
    </Styled.DashboardLink>
  </li>
));

LinkElement.propTypes = {
  to: PropTypes.string,
  dataTest: PropTypes.string,
  title: PropTypes.string,
  isActive: PropTypes.bool,
  label: PropTypes.string
};

export const NavElement = memo(props => (
  <li data-test={props.dataTest}>
    <Styled.ListLink
      isActive={props.isActive}
      title={props.title}
      {...props.linkProps}
    >
      <span>{props.label}</span>
      <Badge isActive={props.isActive} type={props.type}>
        {props.count}
      </Badge>
    </Styled.ListLink>
  </li>
));

NavElement.propTypes = {
  dataTest: PropTypes.string,
  title: PropTypes.string,
  isActive: PropTypes.bool,
  linkProps: PropTypes.object,
  type: PropTypes.string,
  label: PropTypes.string,
  count: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
};

export const DoubleBadgeNavElement = memo(props => (
  <li data-test={props.dataTest}>
    <Styled.ListLink
      to={props.to}
      title={props.title}
      isActive={props.isActive}
      onClick={props.expandSelections}
    >
      <span>{props.label}</span>
      <ComboBadge type={props.type} isActive={props.isActive}>
        <Styled.SelectionBadgeLeft>
          {props.selectionCount}
        </Styled.SelectionBadgeLeft>
        <ComboBadge.Right>{props.instancesInSelectionsCount}</ComboBadge.Right>
      </ComboBadge>
    </Styled.ListLink>
  </li>
));

DoubleBadgeNavElement.propTypes = {
  dataTest: PropTypes.string,
  to: PropTypes.string,
  title: PropTypes.string,
  isActive: PropTypes.bool,
  type: PropTypes.string,
  label: PropTypes.string,
  selectionCount: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  instancesInSelectionsCount: PropTypes.oneOfType([
    PropTypes.number,
    PropTypes.string
  ]),
  expandSelections: PropTypes.func
};
