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

    selectActiveCondition(type: any) {
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
        // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
        return conditions[type];
      }

      // Is 'dashboard' or 'filters' active;
      const conditions = {
        dashboard: currentView.isDashboard(),
        filters: currentView.isInstances() && !this.props.isFiltersCollapsed,
      };

      // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
      return conditions[type];
    }

    selectCount(type: any) {
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

      // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
      return conditions[type];
    }

    getLinkProperties(type: any) {
      const count = this.selectCount(type);

      return {
        count: count,
        isActive: this.selectActiveCondition(type),
        title: createTitle(type, count),
        dataTest: 'header-link-' + type,
        linkProps: {onClick: this.props.expandFilters},
        queryParams: this.getQueryParams(type),
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
            to={{
              pathname: '/login',
              search: getPersistentQueryParams(this.props.location.search),
            }}
          />
        );
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
              to={(location: Location) => ({
                ...location,
                pathname: '/',
                search: mergeQueryParams({
                  newParams: brand.queryParams,
                  prevParams: getPersistentQueryParams(location.search),
                }),
              })}
              dataTest={brand.dataTest}
              title={brand.title}
              label={labels['brand']}
            />
            <LinkElement
              dataTest={dashboard.dataTest}
              to={(location: Location) => ({
                ...location,
                pathname: '/',
                search: mergeQueryParams({
                  newParams: dashboard.queryParams,
                  prevParams: getPersistentQueryParams(location.search),
                }),
              })}
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
              to={(location: Location) => ({
                ...location,
                pathname: '/instances',
                search: mergeQueryParams({
                  newParams: instances.queryParams,
                  prevParams: getPersistentQueryParams(location.search),
                }),
              })}
            />
            <Styled.FilterNavElement
              dataTest={filters.dataTest}
              isActive={filters.isActive}
              title={filters.title}
              label={labels['filters']}
              count={filters.count}
              linkProps={filters.linkProps}
              type={BADGE_TYPE.FILTERS}
              to={(location: Location) => ({
                ...location,
                pathname: '/instances',
                search: mergeQueryParams({
                  newParams: filters.queryParams,
                  prevParams: getPersistentQueryParams(location.search),
                }),
              })}
            />
            <NavElement
              dataTest={incidents.dataTest}
              isActive={incidents.isActive}
              title={incidents.title}
              label={labels['incidents']}
              count={incidents.count}
              linkProps={incidents.linkProps}
              type={BADGE_TYPE.INCIDENTS}
              to={(location: Location) => ({
                ...location,
                pathname: '/instances',
                search: mergeQueryParams({
                  newParams: incidents.queryParams,
                  prevParams: getPersistentQueryParams(location.search),
                }),
              })}
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
