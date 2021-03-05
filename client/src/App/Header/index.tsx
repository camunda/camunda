/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {Location} from 'history';
import {withRouter} from 'react-router';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {statisticsStore} from 'modules/stores/statistics';
import {currentInstanceStore} from 'modules/stores/currentInstance';

import {observer} from 'mobx-react';

import {instancesStore} from 'modules/stores/instances';
import {wrapWithContexts} from 'modules/contexts/contextHelpers';
import {getFilterQueryString, parseQueryString} from 'modules/utils/filter';
import {FILTER_SELECTION, BADGE_TYPE, DEFAULT_FILTER} from 'modules/constants';

import {isEqual} from 'lodash';
import {labels, createTitle, PATHNAME} from './constants';

import {User} from './User';
import {NavElement, BrandNavElement, LinkElement} from './NavElements';
import * as Styled from './styled';
import {mergeQueryParams} from 'modules/utils/mergeQueryParams';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';
import {Locations} from 'modules/routes';
import {IS_FILTERS_V2} from 'modules/utils/filter';

type Props = {
  location: Location;
  isFiltersCollapsed: boolean;
  expandFilters: () => void;
};

type State = {
  forceRedirect: boolean;
  user: unknown;
  filter: null | unknown;
};

const Header = observer(
  class Header extends React.Component<Props, State> {
    state = {
      forceRedirect: false,
      user: {},
      filter: null,
    };

    componentDidMount = () => {
      statisticsStore.init();
    };

    componentDidUpdate = (prevProps: Props) => {
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
      statisticsStore.reset();
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

    getQueryParams = (type: string) => {
      let queryParams: string = '';

      if (type === 'filters') {
        queryParams = this.state.filter
          ? // @ts-expect-error ts-migrate(2554) FIXME: Expected 2 arguments, but got 1.
            getFilterQueryString(this.state.filter)
          : '';
      } else if (type === 'instances') {
        queryParams = `${getFilterQueryString(FILTER_SELECTION.running)}`;
      } else if (type === 'incidents') {
        queryParams = `${getFilterQueryString({
          incidents: true,
        })}`;
      }

      return queryParams;
    };

    selectActiveCondition(type: 'instances' | 'incidents' | 'filters') {
      const currentView = this.currentView();
      const {filter} = this.state;
      const {location} = this.props;

      // Is 'running instances' or 'incidents badge' active;
      if ((type === 'instances' || type === 'incidents') && !IS_FILTERS_V2) {
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
        filters: currentView.isInstances() && !this.props.isFiltersCollapsed,
        instances:
          Locations.runningInstances(location).search ===
          location.search.replace('?', ''),
        incidents:
          Locations.incidents(location).search ===
          location.search.replace('?', ''),
      };

      return conditions[type];
    }

    getCount(type: 'instances' | 'incidents' | 'filters') {
      const {running, withIncidents, status} = statisticsStore.state;
      const {filteredInstancesCount} = instancesStore.state;

      if (['initial', 'first-fetch'].includes(status)) {
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

    getLinkProperties(type: 'instances' | 'incidents' | 'filters') {
      const count = this.getCount(type);

      return {
        count: count,
        title: createTitle(type, count.toString()),
      };
    }

    renderInstanceDetails() {
      const {instance} = currentInstanceStore.state;
      if (instance) {
        return (
          <>
            <Styled.StateIcon
              data-testid={`state-icon-${instance.state}`}
              state={instance.state}
            />
            Instance {instance.id}
          </>
        );
      } else {
        return (
          <>
            <Styled.SkeletonCircle data-testid="instance-skeleton-circle" />
            <Styled.SkeletonBlock data-testid="instance-skeleton-block" />
          </>
        );
      }
    }

    render() {
      if (this.state.forceRedirect) {
        return (
          <Redirect
            to={
              IS_FILTERS_V2
                ? Locations.login(this.props.location)
                : {
                    pathname: '/login',
                    search: getPersistentQueryParams(
                      this.props.location.search
                    ),
                  }
            }
          />
        );
      }

      const instances = this.getLinkProperties('instances');
      const incidents = this.getLinkProperties('incidents');
      const filters = this.getLinkProperties('filters');

      return (
        <Styled.Header>
          <Styled.Menu role="navigation">
            <BrandNavElement
              to={
                IS_FILTERS_V2
                  ? (location: Location) => Locations.dashboard(location)
                  : (location: Location) => ({
                      ...location,
                      pathname: '/',
                      search: getPersistentQueryParams(location.search),
                    })
              }
              dataTest="header-link-brand"
              title="View Dashboard"
              label={labels.brand}
            />
            <LinkElement
              dataTest="header-link-dashboard"
              to={
                IS_FILTERS_V2
                  ? (location: Location) => Locations.dashboard(location)
                  : (location: Location) => ({
                      ...location,
                      pathname: '/',
                      search: getPersistentQueryParams(location.search),
                    })
              }
              isActive={this.currentView().isDashboard()}
              title="View Dashboard"
              label={labels.dashboard}
            />
            <NavElement
              dataTest="header-link-instances"
              isActive={this.selectActiveCondition('instances')}
              title={instances.title}
              label={labels.instances}
              count={instances.count}
              linkProps={{onClick: this.props.expandFilters}}
              type={BADGE_TYPE.RUNNING_INSTANCES}
              to={
                IS_FILTERS_V2
                  ? (location: Location) => Locations.runningInstances(location)
                  : (location: Location) => ({
                      ...location,
                      pathname: '/instances',
                      search: mergeQueryParams({
                        newParams: this.getQueryParams('instances'),
                        prevParams: getPersistentQueryParams(location.search),
                      }),
                    })
              }
            />
            <Styled.FilterNavElement
              dataTest="header-link-filters"
              isActive={this.selectActiveCondition('filters')}
              title={filters.title}
              label={labels.filters}
              count={filters.count}
              linkProps={{onClick: this.props.expandFilters}}
              type={BADGE_TYPE.FILTERS}
              to={
                IS_FILTERS_V2
                  ? (location: Location) => Locations.filters(location)
                  : (location: Location) => ({
                      ...location,
                      pathname: '/instances',
                      search: mergeQueryParams({
                        newParams: this.getQueryParams('filters'),
                        prevParams: getPersistentQueryParams(location.search),
                      }),
                    })
              }
            />
            <NavElement
              dataTest="header-link-incidents"
              isActive={this.selectActiveCondition('incidents')}
              title={incidents.title}
              label={labels.incidents}
              count={incidents.count}
              linkProps={{onClick: this.props.expandFilters}}
              type={BADGE_TYPE.INCIDENTS}
              to={
                IS_FILTERS_V2
                  ? (location: Location) => Locations.incidents(location)
                  : (location: Location) => ({
                      ...location,
                      pathname: '/instances',
                      search: mergeQueryParams({
                        newParams: this.getQueryParams('incidents'),
                        prevParams: getPersistentQueryParams(location.search),
                      }),
                    })
              }
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

// @ts-expect-error ts-migrate(2345) FIXME: Type '(Component: any) => { (props: any): JSX.Elem... Remove this comment to see the full error message
const WrappedHeader = wrapWithContexts(contexts, Header);

WrappedHeader.WrappedComponent = Header;

export default WrappedHeader;
