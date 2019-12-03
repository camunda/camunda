/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';
import {withData} from 'modules/DataManager';

import * as api from 'modules/api/header';
import withSharedState from 'modules/components/withSharedState';
import {getFilterQueryString} from 'modules/utils/filter';
import {
  LOADING_STATE,
  FILTER_SELECTION,
  BADGE_TYPE,
  COMBO_BADGE_TYPE,
  DEFAULT_FILTER,
  SUBSCRIPTION_TOPIC
} from 'modules/constants';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {isEqual} from 'lodash';

import {localStateKeys, labels, createTitle} from './constants';
import User from './User';
import {
  DoubleBadgeNavElement,
  NavElement,
  BrandNavElement,
  LinkElement
} from './NavElements';
import * as Styled from './styled.js';

class Header extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
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
  static labels = labels;
  constructor(props) {
    super(props);
    this.subscriptions = {
      LOAD_CORE_STATS: ({state, response}) => {
        if (state === LOADING_STATE.LOADED) {
          this.updateCounts(response.coreStatistics);
        }
      },
      REFRESH_AFTER_OPERATION: ({state, response}) => {
        if (state === LOADING_STATE.LOADED) {
          this.updateCounts(
            response[SUBSCRIPTION_TOPIC.LOAD_CORE_STATS].coreStatistics
          );
        }
      }
    };
  }

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
    this.props.dataManager.subscribe(this.subscriptions);

    this.setUser();
    this.localState = this.props.getStateLocally();
    localStateKeys.forEach(this.setValueFromPropsOrLocalState);

    this.setValuesFromPropsOrApi();

    // set static labels.
  };

  componentDidUpdate = (prevProps, prevState) => {
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

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  updateCounts({running, withIncidents}) {
    const {runningInstancesCount, incidentsCount} = this.props;
    const counts = {runningInstancesCount, incidentsCount};

    counts.runningInstancesCount = running || 0;
    counts.incidentsCount = withIncidents || 0;

    this.setState(counts);
  }

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
    if (
      typeof runningInstancesCount === 'undefined' ||
      typeof incidentsCount === 'undefined'
    ) {
      this.props.dataManager.getWorkflowCoreStatistics();
    } else {
      this.setState({runningInstancesCount, incidentsCount});
    }
  };

  handleLogout = async () => {
    await api.logout();
    this.setState({forceRedirect: true});
  };

  getFilterResetProps = type => {
    const filters = {
      instances: DEFAULT_FILTER,
      filters: this.state.filter || {},
      incidents: {incidents: true}
    };
    return {
      onClick: e => {
        e.preventDefault();
        this.props.expandFilters();
        // TODO: empty default
        this.props.onFilterReset(filters[type]);
      },
      to: ' '
    };
  };

  getListLinksProps = type => {
    if (this.props.onFilterReset) {
      return this.getFilterResetProps(type);
    }

    const queryStrings = {
      filters: this.state.filter ? getFilterQueryString(this.state.filter) : '',
      instances: getFilterQueryString(FILTER_SELECTION.running),
      incidents: getFilterQueryString({incidents: true})
    };

    return {
      to: `/instances${queryStrings[type]}`,
      onClick: this.props.expandFilters
    };
  };

  selectTitle(type) {
    const titles = {
      brand: 'View Dashboard',
      dashboard: 'View Dashboard',
      instances: `View ${this.state.runningInstancesCount} Running Instances`,
      filters: `View ${this.state.filterCount} Instances in Filters`,
      incidents: `View ${this.state.incidentsCount} Incidents`,
      selections: `View ${this.state.selectionCount} Selections`
    };
    return titles[type];
  }

  selectActiveCondition(type) {
    const {active} = this.props;
    const {filter} = this.state;

    if (type === 'instances' || type === 'incidents') {
      const isRunningInstanceFilter = isEqual(filter, FILTER_SELECTION.running);

      const isIncidentsFilter =
        !isRunningInstanceFilter && isEqual(filter, {incidents: true});

      const conditions = {
        instances: active === 'instances' && isRunningInstanceFilter,
        incidents: active === 'instances' && isIncidentsFilter
      };
      return conditions[type];
    }

    const conditions = {
      dashboard: active === 'dashboard',
      filters: active === 'instances' && !this.props.isFiltersCollapsed,
      selections: active === 'instances' && !this.props.isSelectionsCollapsed
    };

    return conditions[type];
  }

  selectCount(type) {
    const {
      runningInstancesCount,
      filterCount,
      filter,
      instancesInSelectionsCount,
      selectionCount,
      incidentsCount
    } = this.state;

    const conditions = {
      instances: runningInstancesCount,
      filters: isEqual(filter, FILTER_SELECTION.running)
        ? runningInstancesCount
        : filterCount,
      incidents: incidentsCount,
      selections: {instancesInSelectionsCount, selectionCount}
    };

    return conditions[type];
  }

  getLinkProperties(type) {
    const count = this.selectCount(type);
    return {
      count: count,
      isActive: this.selectActiveCondition(type),
      title: createTitle(type, count),
      dataTest: 'header-link-' + type,
      linkProps: this.getListLinksProps(type)
    };
  }

  render() {
    if (this.state.forceRedirect) {
      return <Redirect to="/login" />;
    }
    const {detail} = this.props;
    const {filter} = this.state;
    const {firstname, lastname, canLogout = true} = this.state.user || {};

    const brand = this.getLinkProperties('brand');
    const dashboard = this.getLinkProperties('dashboard');
    const instance = this.getLinkProperties('instances');
    const incidents = this.getLinkProperties('incidents');
    const filters = this.getLinkProperties('filters');
    const selections = this.getLinkProperties('selections');

    return (
      <Styled.Header role="banner">
        <Styled.Menu role="navigation">
          <BrandNavElement
            to="/"
            dataTest={brand.dataTest}
            title={brand.title}
            label={Header.labels['brand']}
          />
          <LinkElement
            dataTest={dashboard.dataTest}
            to="/"
            isActive={dashboard.isActive}
            title={dashboard.title}
            label={Header.labels['dashboard']}
          />

          <NavElement
            dataTest={instance.dataTest}
            isActive={instance.isActive}
            title={instance.title}
            label={Header.labels['instances']}
            count={instance.count}
            linkProps={instance.linkProps}
            type={BADGE_TYPE.RUNNING_INSTANCES}
          />
          <NavElement
            dataTest={filters.dataTest}
            isActive={filters.isActive}
            title={filters.title}
            label={Header.labels['filters']}
            count={this.props.filterCount || filters.count}
            linkProps={filters.linkProps}
            type={BADGE_TYPE.FILTERS}
          />
          <NavElement
            dataTest={incidents.dataTest}
            isActive={incidents.isActive}
            title={incidents.title}
            label={Header.labels['incidents']}
            count={incidents.count}
            linkProps={incidents.linkProps}
            type={BADGE_TYPE.INCIDENTS}
          />

          <DoubleBadgeNavElement
            to={`/instances${filter ? getFilterQueryString(filter) : ''}`}
            dataTest={selections.dataTest}
            title={selections.title}
            label={Header.labels['selections']}
            isActive={selections.isActive}
            expandSelections={this.props.expandSelections}
            selectionCount={
              this.props.selectionCount || selections.count.selectionCount
            }
            instancesInSelectionsCount={
              this.props.instancesInSelectionsCount ||
              selections.count.instancesInSelectionsCount
            }
            type={COMBO_BADGE_TYPE.SELECTIONS}
          />
        </Styled.Menu>
        <Styled.Detail>{detail}</Styled.Detail>
        <User
          handleLogout={this.handleLogout}
          firstname={firstname}
          lastname={lastname}
          canLogout={canLogout}
        />
      </Styled.Header>
    );
  }
}

const WrappedHeader = withData(
  withSelection(withCollapsablePanel(withSharedState(Header)))
);
WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
