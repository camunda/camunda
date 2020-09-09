/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';
import {withRouter} from 'react-router';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {statistics} from 'modules/stores/statistics';
import {currentInstance} from 'modules/stores/currentInstance';

import {observer} from 'mobx-react';

import {instances} from 'modules/stores/instances';
import {wrapWithContexts} from 'modules/contexts/contextHelpers';
import {getFilterQueryString, parseQueryString} from 'modules/utils/filter';
import {FILTER_SELECTION, BADGE_TYPE, DEFAULT_FILTER} from 'modules/constants';

import {isEqual} from 'lodash';
import {labels, createTitle, PATHNAME} from './constants';

import User from './User';
import {NavElement, BrandNavElement, LinkElement} from './NavElements';
import * as Styled from './styled.js';

const Header = observer(
  class Header extends React.Component {
    static propTypes = {
      location: PropTypes.object,
      isFiltersCollapsed: PropTypes.bool.isRequired,
      expandFilters: PropTypes.func.isRequired,
      onFilterReset: PropTypes.func,
    };

    constructor(props) {
      super(props);

      this.state = {
        forceRedirect: false,
        user: {},
        filter: null,
      };
    }

    componentDidMount = () => {
      statistics.init();
    };

    componentDidUpdate = (prevProps) => {
      const {location} = this.props;

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
      statistics.reset();
    }

    currentView() {
      const {DASHBOARD, INSTANCES, INSTANCE} = PATHNAME;
      const {pathname} = this.props.location;

      return {
        isDashboard: () => pathname === DASHBOARD,
        isInstances: () => pathname === INSTANCES,
        isInstance: () => pathname.includes(INSTANCE),
      };
    }

    handleRedirect = () => {
      this.setState({forceRedirect: true});
    };

    getFilterResetProps = (type) => {
      const filters = {
        instances: DEFAULT_FILTER,
        filters: this.state.filter || {},
        incidents: {incidents: true},
      };
      return {
        onClick: (e) => {
          e.preventDefault();
          this.props.expandFilters();
          this.props.onFilterReset(filters[type]);
        },
        to: ' ',
      };
    };

    getListLinksProps = (type) => {
      if (this.props.onFilterReset) {
        return this.getFilterResetProps(type);
      }

      const queryStrings = {
        filters: this.state.filter
          ? getFilterQueryString(this.state.filter)
          : '',
        instances: getFilterQueryString(FILTER_SELECTION.running),
        incidents: getFilterQueryString({incidents: true}),
      };

      return {
        to: `/instances${queryStrings[type]}`,
        onClick: this.props.expandFilters,
      };
    };

    selectActiveCondition(type) {
      const currentView = this.currentView();
      const {filter} = this.state;

      // Is 'running instances' or 'incidents badge' active;
      if (type === 'instances' || type === 'incidents') {
        const isRunningInstanceFilter = isEqual(
          filter,
          FILTER_SELECTION.running
        );
        const conditions = {
          instances: currentView.isInstances() && isRunningInstanceFilter,
          incidents:
            currentView.isInstances() &&
            !isRunningInstanceFilter &&
            isEqual(filter, {incidents: true}),
        };
        return conditions[type];
      }

      // Is 'dashboard' or 'filters' active;
      const conditions = {
        dashboard: currentView.isDashboard(),
        filters: currentView.isInstances() && !this.props.isFiltersCollapsed,
      };

      return conditions[type];
    }

    selectCount(type) {
      const {running, withIncidents, isLoaded} = statistics.state;
      const {filteredInstancesCount} = instances.state;

      if (!isLoaded) {
        return '';
      }

      const conditions = {
        instances: running,
        filters:
          filteredInstancesCount === null ? running : filteredInstancesCount,
        incidents: withIncidents,
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
        linkProps: this.getListLinksProps(type),
      };
    }

    renderInstanceDetails() {
      const {instance} = currentInstance.state;
      if (instance) {
        return (
          <>
            <Styled.StateIcon
              data-test={`state-icon-${instance.state}`}
              state={instance.state}
            />
            Instance {instance.id}
          </>
        );
      } else {
        return (
          <>
            <Styled.SkeletonCircle data-test="instance-skeleton-circle" />
            <Styled.SkeletonBlock data-test="instance-skeleton-block" />
          </>
        );
      }
    }

    render() {
      if (this.state.forceRedirect) {
        return <Redirect to="/login" />;
      }

      const brand = this.getLinkProperties('brand');
      const dashboard = this.getLinkProperties('dashboard');
      const instances = this.getLinkProperties('instances');
      const incidents = this.getLinkProperties('incidents');
      const filters = this.getLinkProperties('filters');

      return (
        <Styled.Header>
          <Styled.Menu role="navigation">
            <BrandNavElement
              to="/"
              dataTest={brand.dataTest}
              title={brand.title}
              label={labels['brand']}
            />
            <LinkElement
              dataTest={dashboard.dataTest}
              to="/"
              isActive={dashboard.isActive}
              title={dashboard.title}
              label={labels['dashboard']}
            />
            <NavElement
              dataTest={instances.dataTest}
              isActive={instances.isActive}
              title={instances.title}
              label={labels['instances']}
              count={instances.count}
              linkProps={instances.linkProps}
              type={BADGE_TYPE.RUNNING_INSTANCES}
            />
            <Styled.FilterNavElement
              dataTest={filters.dataTest}
              isActive={filters.isActive}
              title={filters.title}
              label={labels['filters']}
              count={filters.count}
              linkProps={filters.linkProps}
              type={BADGE_TYPE.FILTERS}
            />
            <NavElement
              dataTest={incidents.dataTest}
              isActive={incidents.isActive}
              title={incidents.title}
              label={labels['incidents']}
              count={incidents.count}
              linkProps={incidents.linkProps}
              type={BADGE_TYPE.INCIDENTS}
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
);

const contexts = [withCollapsablePanel, withRouter];

const WrappedHeader = wrapWithContexts(contexts, Header);

WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
