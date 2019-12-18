/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';
import {withData} from 'modules/DataManager';
import {withCountStore} from 'modules/contexts/CountContext';
import {withRouter} from 'react-router';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {withSelection} from 'modules/contexts/SelectionContext';

import {wrapWithContexts} from 'modules/contexts/contextHelpers';
import withSharedState from 'modules/components/withSharedState';
import {getFilterQueryString, parseQueryString} from 'modules/utils/filter';
import {
  FILTER_SELECTION,
  BADGE_TYPE,
  COMBO_BADGE_TYPE,
  LOADING_STATE,
  DEFAULT_FILTER
} from 'modules/constants';

import {isEqual} from 'lodash';
import {labels, createTitle, PATHNAME} from './constants';

import User from './User';
import InstanceDetail from './InstanceDetail';
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
    countStore: PropTypes.shape({
      running: PropTypes.number,
      active: PropTypes.number,
      filterCount: PropTypes.number,
      withIncidents: PropTypes.number,
      instancesInSelectionsCount: PropTypes.number,
      selectionCount: PropTypes.number
    }),
    location: PropTypes.object,
    selectionCount: PropTypes.number,
    instancesInSelectionsCount: PropTypes.number,
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
      LOAD_INSTANCE: ({state, response}) => {
        if (state === LOADING_STATE.LOADING) {
          this.setState({
            instance: null
          });
        }
        if (state === LOADING_STATE.LOADED) {
          this.setState({
            instance: response
          });
        }
      }
    };
    this.state = {
      forceRedirect: false,
      user: {},
      instance: null,
      filter: null,
      isLoaded: false
    };
  }

  componentDidMount = () => {
    this.props.dataManager.subscribe(this.subscriptions);

    const isLoaded = this.areCountsLoaded();
    if (isLoaded) {
      this.setState({isLoaded});
    }
  };

  componentDidUpdate = (prevProps, prevState) => {
    const {location} = this.props;
    const isLoaded = this.areCountsLoaded();

    if (prevState.isLoaded !== isLoaded) {
      this.setState({isLoaded});
    }

    // Instances View: Set filter count from URL
    if (
      this.currentView().isInstances() &&
      prevProps.location.search !== location.search
    ) {
      const filterFromURL = parseQueryString(location.search).filter;
      this.setState({filter: filterFromURL});
    } else if (!this.state.filter) {
      this.setState({filter: DEFAULT_FILTER});
    }
  };

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  currentView() {
    const {DASHBOARD, INSTANCES, INSTANCE} = PATHNAME;
    const {pathname} = this.props.location;

    return {
      isDashboard: () => pathname === DASHBOARD,
      isInstances: () => pathname === INSTANCES,
      isInstance: () => pathname.includes(INSTANCE)
    };
  }

  areCountsLoaded() {
    const {
      filterCount,
      selectionCount,
      instancesInSelectionsCount,
      ...includedCounts
    } = this.props.countStore;

    return Object.values(includedCounts).every(count => count > 0);
  }

  handleRedirect = () => {
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

  selectActiveCondition(type) {
    const currentView = this.currentView();
    const {filter} = this.state;

    // Is 'running instances' or 'incidents badge' active;
    if (type === 'instances' || type === 'incidents') {
      const isRunningInstanceFilter = isEqual(filter, FILTER_SELECTION.running);
      const conditions = {
        instances: currentView.isInstances() && isRunningInstanceFilter,
        incidents:
          currentView.isInstances() &&
          !isRunningInstanceFilter &&
          isEqual(filter, {incidents: true})
      };
      return conditions[type];
    }

    // Is 'dashboard', 'filters' or 'selections' active;
    const conditions = {
      dashboard: currentView.isDashboard(),
      filters: currentView.isInstances() && !this.props.isFiltersCollapsed,
      selections: currentView.isInstances() && !this.props.isSelectionsCollapsed
    };

    return conditions[type];
  }

  selectCount(type) {
    const {
      running,
      withIncidents,
      instancesInSelectionsCount,
      selectionCount,
      filterCount
    } = this.props.countStore;

    if (!this.state.isLoaded) {
      return '';
    }

    const conditions = {
      instances: running,
      filters: filterCount === null ? running : filterCount,
      incidents: withIncidents,
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

  renderInstanceDetails() {
    if (this.state.instance) {
      return <InstanceDetail instance={this.state.instance} />;
    } else {
      return (
        <>
          <Styled.SkeletonCircle />
          <Styled.SkeletonBlock />
        </>
      );
    }
  }

  render() {
    if (this.state.forceRedirect) {
      return <Redirect to="/login" />;
    }

    const {filter} = this.state;

    const brand = this.getLinkProperties('brand');
    const dashboard = this.getLinkProperties('dashboard');
    const instances = this.getLinkProperties('instances');
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
            dataTest={instances.dataTest}
            isActive={instances.isActive}
            title={instances.title}
            label={Header.labels['instances']}
            count={instances.count}
            linkProps={instances.linkProps}
            type={BADGE_TYPE.RUNNING_INSTANCES}
          />
          <NavElement
            dataTest={filters.dataTest}
            isActive={filters.isActive}
            title={filters.title}
            label={Header.labels['filters']}
            count={filters.count}
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
            selectionCount={selections.count.selectionCount}
            instancesInSelectionsCount={
              selections.count.instancesInSelectionsCount
            }
            type={COMBO_BADGE_TYPE.SELECTIONS}
          />
        </Styled.Menu>
        {this.currentView().isInstance() && (
          <Styled.Detail>{this.renderInstanceDetails()}</Styled.Detail>
        )}
        <User handleRedirect={this.handleRedirect} />
      </Styled.Header>
    );
  }
}

const contexts = [
  withCountStore,
  withData,
  withSelection,
  withCollapsablePanel,
  withSharedState,
  withRouter,
  withData
];

const WrappedHeader = wrapWithContexts(contexts, Header);

WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
