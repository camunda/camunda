import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';

import Dropdown from 'modules/components/Dropdown';
import ComboBadge from 'modules/components/ComboBadge';
import * as api from 'modules/api/header';
import withSharedState from 'modules/components/withSharedState';
import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {getFilterQueryString} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';

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
    getStateLocally: PropTypes.func.isRequired
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
    const isInstancesPage = active === 'instances';

    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      <Styled.Header>
        <Styled.Menu role="navigation">
          <li>
            <Styled.Dashboard to="/" isActive={active === 'dashboard'}>
              <Styled.LogoIcon />
              <span>Dashboard</span>
            </Styled.Dashboard>
          </li>
          <li data-test="header-link-instances">
            <Styled.ListLink
              isActive={isInstancesPage}
              to="/instances"
              title={`${this.state.runningInstancesCount} Instances`}
            >
              <span>Instances</span>
              <Styled.RunningInstancesBadge>
                {this.state.runningInstancesCount}
              </Styled.RunningInstancesBadge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-filters">
            <Styled.ListLink
              isActive={isInstancesPage}
              to={`/instances${getFilterQueryString(this.state.filter)}`}
              title={`${this.state.filterCount} Filters`}
            >
              <span>Filters</span>
              <Styled.FiltersBadge type="filters">
                {this.state.filterCount}
              </Styled.FiltersBadge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-selections">
            <Styled.ListLink
              to="/instances"
              title={`${this.state.instancesInSelectionsCount} ${
                this.state.selectionCount
              } Selections`}
              isActive={isInstancesPage}
            >
              <span>Selections</span>
              <ComboBadge>
                <Styled.SelectionBadgeLeft>
                  {this.state.instancesInSelectionsCount}
                </Styled.SelectionBadgeLeft>
                <Styled.SelectionBadgeRight>
                  {this.state.selectionCount}
                </Styled.SelectionBadgeRight>
              </ComboBadge>
            </Styled.ListLink>
          </li>
          <li data-test="header-link-incidents">
            <Styled.ListLink
              isActive={isInstancesPage}
              to={`/instances${incidentsQuery}`}
              title={`${this.state.incidentsCount} Incidents`}
            >
              <span>Incidents</span>
              <Styled.IncidentsBadge type="incidents">
                {this.state.incidentsCount}
              </Styled.IncidentsBadge>
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

export default withSharedState(Header);
