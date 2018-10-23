import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import * as api from 'modules/api/header';
import withSharedState from 'modules/components/withSharedState';
import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {getFilterQueryString} from 'modules/utils/filter';
import {
  FILTER_SELECTION,
  BADGE_TYPE,
  COMBO_BADGE_TYPE
} from 'modules/constants';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import {isEqual} from 'lodash';

import {filtersMap, localStateKeys, apiKeys} from './constants';
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
    expandSelections: PropTypes.func.isRequired
  };

  state = {
    forceRedirect: false,
    user: {},
    runningInstancesCount: 0,
    selectionCount: 0,
    filterCount: 0,
    filter: {},
    instancesInSelectionsCount: 0,
    incidentsCount: 0
  };

  componentDidMount = async () => {
    this.setUser();
    this.localState = this.props.getStateLocally();
    localStateKeys.forEach(this.setValueFromPropsOrLocalState);
    apiKeys.forEach(this.setValueFromPropsOrApi);
  };

  componentDidUpdate = prevProps => {
    localStateKeys.forEach(key => {
      if (this.props[key] !== prevProps[key]) {
        this.setValueFromPropsOrLocalState(key);
      }
    });

    apiKeys.forEach(key => {
      if (this.props[key] !== prevProps[key]) {
        this.setValueFromPropsOrApi(key);
      }
    });
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
    let value =
      typeof this.props[key] === 'undefined'
        ? this.localState[key]
        : this.props[key];

    if (typeof value !== 'undefined') {
      this.setState({[key]: value});
    }
  };

  /**
   * Sets value in the state by getting it from the props or falling back to the api.
   * @param {string} key: key for which to set the value
   */
  setValueFromPropsOrApi = async key => {
    let countValue = this.props[key];

    // if it's not provided in props, fetch it from the api
    if (typeof countValue === 'undefined') {
      countValue = await fetchWorkflowInstancesCount(filtersMap[key]);
    }

    this.setState({[key]: countValue});
  };

  handleLogout = async () => {
    await api.logout();
    this.setState({forceRedirect: true});
  };

  render() {
    const {active, detail} = this.props;
    const {firstname, lastname} = this.state.user || {};

    // query for the incidents link
    const incidentsQuery = getFilterQueryString(FILTER_SELECTION.incidents);
    const runningQuery = getFilterQueryString(FILTER_SELECTION.running);
    const filterQuery = getFilterQueryString(this.state.filter);

    const isRunningInstanceFilter = isEqual(
      FILTER_SELECTION.running,
      this.state.filter
    );
    // P.S. checking isRunningInstanceFilter first is a small perf improvement because
    // it checks for isRunningInstanceFilter before making an object equality check
    const isIncidentsFilter =
      !isRunningInstanceFilter && isEqual({incidents: true}, this.state.filter);

    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      <Styled.Header>
        <Styled.Menu role="navigation">
          <li>
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
              to={`/instances${runningQuery}`}
              title={`View ${
                this.state.runningInstancesCount
              } Running Instances`}
              onClick={this.props.expandFilters}
            >
              <span>Running Instances</span>
              <Badge type={BADGE_TYPE.RUNNING_INSTANCES}>
                {this.state.runningInstancesCount}
              </Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-filters">
            <Styled.ListLink
              isActive={
                active === 'instances' && !this.props.isFiltersCollapsed
              }
              to={`/instances${filterQuery}`}
              title={`View ${this.state.filterCount} Instances in Filters`}
              onClick={this.props.expandFilters}
            >
              <span>Filters</span>
              <Badge type={BADGE_TYPE.FILTERS}>{this.state.filterCount}</Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-incidents">
            <Styled.ListLink
              isActive={active === 'instances' && isIncidentsFilter}
              to={`/instances${incidentsQuery}`}
              title={`View ${this.state.incidentsCount} Incidents`}
              onClick={this.props.expandFilters}
            >
              <span>Incidents</span>
              <Badge type={BADGE_TYPE.INCIDENTS}>
                {this.state.incidentsCount}
              </Badge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-selections">
            <Styled.ListLink
              to={`/instances${runningQuery}`}
              title={`View ${this.state.selectionCount} ${
                this.state.instancesInSelectionsCount
              } Selections`}
              isActive={
                active === 'instances' && !this.props.isSelectionsCollapsed
              }
              onClick={this.props.expandSelections}
            >
              <span>Selections</span>
              <ComboBadge type={COMBO_BADGE_TYPE.SELECTIONS}>
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
          <Dropdown label={`${firstname} ${lastname}`}>
            <Dropdown.Option
              label="Logout"
              data-test="logout-button"
              onClick={this.handleLogout}
            />
          </Dropdown>
        </Styled.ProfileDropdown>
      </Styled.Header>
    );
  }
}

export default withCollapsablePanel(withSharedState(Header));
