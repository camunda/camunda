/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import * as api from 'modules/api/header';
import withSharedState from 'modules/components/withSharedState';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {getFilterQueryString} from 'modules/utils/filter';
import {
  FILTER_SELECTION,
  BADGE_TYPE,
  COMBO_BADGE_TYPE,
  DEFAULT_FILTER
} from 'modules/constants';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';
import {ThemeConsumer} from 'modules/theme';

import {isEqual} from 'lodash';

import {localStateKeys} from './constants';
import * as Styled from './styled.js';

class Header extends React.Component {
  static propTypes = {
    active: PropTypes.oneOf(['dashboard', 'instances']),
    runningInstancesCount: PropTypes.number,
    filter: PropTypes.object,
    filterCount: PropTypes.number,
    selectionCount: PropTypes.number,
    instancesInSelectionsCount: PropTypes.number,
    incidentsCount: PropTypes.number,
    detail: PropTypes.element,
    getStateLocally: PropTypes.func.isRequired,
    isFiltersCollapsed: PropTypes.bool.isRequired,
    isSelectionsCollapsed: PropTypes.bool.isRequired,
    expandFilters: PropTypes.func.isRequired,
    expandSelections: PropTypes.func.isRequired,
    onFilterReset: PropTypes.func
  };

  state = {
    forceRedirect: false,
    user: {},
    runningInstancesCount: 0,
    selectionCount: 0,
    filterCount: 0,
    filter: null,
    instancesInSelectionsCount: 0,
    incidentsCount: 0
  };

  componentDidMount = () => {
    this.setUser();
    this.localState = this.props.getStateLocally();
    localStateKeys.forEach(this.setValueFromPropsOrLocalState);

    this.setValuesFromPropsOrApi();
  };

  componentDidUpdate = prevProps => {
    localStateKeys.forEach(key => {
      if (this.props[key] !== prevProps[key]) {
        this.setValueFromPropsOrLocalState(key);
      }
    });

    if (
      this.props.incidentsCount !== prevProps.incidentsCount ||
      this.props.runningInstancesCount !== prevProps.runningInstancesCount
    ) {
      this.setValuesFromPropsOrApi();
    }

    // set default filter when no filter is found in localState
    if (!this.state.filter) {
      this.setState({filter: DEFAULT_FILTER});
    }
  };

  setUser = async () => {
    const user = await api.fetchUser();
    this.setState({user});
  };

  /**
   * Sets value in the state by getting it from the props or falling back to the local storage.
   * @param {string} key: key for which to set the value
   */
  setValueFromPropsOrLocalState = key => {
    const value =
      typeof this.props[key] === 'undefined'
        ? this.localState[key]
        : this.props[key];

    if (typeof value !== 'undefined') {
      this.setState({[key]: value});
    }
  };

  /**
   * Sets values in the state by getting it from the props or falling back to the api.
   */
  setValuesFromPropsOrApi = async () => {
    const {runningInstancesCount, incidentsCount} = this.props;
    const counts = {runningInstancesCount, incidentsCount};

    if (
      typeof runningInstancesCount === 'undefined' ||
      typeof incidentsCount === 'undefined'
    ) {
      const response = await fetchWorkflowCoreStatistics();
      const {running, withIncidents} = response.data;

      counts.runningInstancesCount = running || 0;
      counts.incidentsCount = withIncidents || 0;
    }

    this.setState(counts);
  };

  handleLogout = async () => {
    await api.logout();
    this.setState({forceRedirect: true});
  };

  getListLinksProps = key => {
    if (this.props.onFilterReset) {
      const filters = {
        instances: DEFAULT_FILTER,
        filters: this.state.filter || {},
        incidents: {incidents: true}
      };
      return {
        onClick: e => {
          e.preventDefault();
          this.props.expandFilters();
          this.props.onFilterReset(filters[key]);
        },
        to: ' '
      };
    }
    const queryStrings = {
      filters: this.state.filter ? getFilterQueryString(this.state.filter) : '',
      instances: getFilterQueryString(FILTER_SELECTION.running),
      incidents: getFilterQueryString({incidents: true})
    };

    return {
      to: `/instances${queryStrings[key]}`,
      onClick: this.props.expandFilters
    };
  };

  render() {
    const {active, detail} = this.props;
    const {firstname, lastname} = this.state.user || {};

    const filterQuery = this.state.filter
      ? getFilterQueryString(this.state.filter)
      : '';

    const isRunningInstanceFilter = isEqual(
      this.state.filter,
      FILTER_SELECTION.running
    );

    // P.S. checking isRunningInstanceFilter first is a small perf improvement because
    // it checks for isRunningInstanceFilter before making an object equality check
    const isIncidentsFilter =
      !isRunningInstanceFilter && isEqual(this.state.filter, {incidents: true});

    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      <Styled.Header role="banner">
        <Styled.Menu role="navigation">
          <li data-test="header-link-dashboard">
            <Styled.Dashboard
              to="/"
              isActive={active === 'dashboard'}
              title="View Dashboard"
            >
              <Styled.LogoIcon />
              <span>Dashboard</span>
            </Styled.Dashboard>
          </li>
          <li data-test="header-link-instances">
            <Styled.ListLink
              isActive={active === 'instances' && isRunningInstanceFilter}
              title={`View ${
                this.state.runningInstancesCount
              } Running Instances`}
              {...this.getListLinksProps('instances')}
            >
              <span>Running Instances</span>
              <Badge
                isActive={active === 'instances' && isRunningInstanceFilter}
                type={BADGE_TYPE.RUNNING_INSTANCES}
              >
                {this.state.runningInstancesCount}
              </Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-filters">
            <Styled.ListLink
              isActive={
                active === 'instances' && !this.props.isFiltersCollapsed
              }
              title={`View ${this.state.filterCount} Instances in Filters`}
              {...this.getListLinksProps('filters')}
            >
              <span>Filters</span>
              <Badge
                type={BADGE_TYPE.FILTERS}
                isActive={
                  active === 'instances' && !this.props.isFiltersCollapsed
                }
              >
                {isRunningInstanceFilter
                  ? this.state.runningInstancesCount
                  : this.state.filterCount}
              </Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-incidents">
            <Styled.ListLink
              isActive={active === 'instances' && isIncidentsFilter}
              title={`View ${this.state.incidentsCount} Incidents`}
              {...this.getListLinksProps('incidents')}
            >
              <span>Incidents</span>
              <Badge
                type={BADGE_TYPE.INCIDENTS}
                isActive={active === 'instances' && isIncidentsFilter}
              >
                {this.state.incidentsCount}
              </Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-selections">
            <Styled.ListLink
              to={`/instances${filterQuery}`}
              title={`View ${this.state.selectionCount} Selections`}
              isActive={
                active === 'instances' && !this.props.isSelectionsCollapsed
              }
              onClick={this.props.expandSelections}
            >
              <span>Selections</span>
              <ComboBadge
                type={COMBO_BADGE_TYPE.SELECTIONS}
                isActive={
                  active === 'instances' && !this.props.isSelectionsCollapsed
                }
              >
                <Styled.SelectionBadgeLeft>
                  {this.state.selectionCount}
                </Styled.SelectionBadgeLeft>
                <ComboBadge.Right>
                  {this.state.instancesInSelectionsCount}
                </ComboBadge.Right>
              </ComboBadge>
            </Styled.ListLink>
          </li>
        </Styled.Menu>

        <Styled.Detail>{detail}</Styled.Detail>
        <Styled.ProfileDropdown>
          <ThemeConsumer>
            {({toggleTheme}) => (
              <Dropdown label={`${firstname} ${lastname}`}>
                <Dropdown.Option
                  label="Toggle Theme"
                  data-test="toggle-theme-button"
                  onClick={toggleTheme}
                />

                <Dropdown.Option
                  label="Logout"
                  data-test="logout-button"
                  onClick={this.handleLogout}
                />
              </Dropdown>
            )}
          </ThemeConsumer>
        </Styled.ProfileDropdown>
      </Styled.Header>
    );
  }
}

const WrappedHeader = withSelection(
  withCollapsablePanel(withSharedState(Header))
);
WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
