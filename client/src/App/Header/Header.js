import React from 'react';
import {Redirect, Link} from 'react-router-dom';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

import Dropdown from 'modules/components/Dropdown';
import {Logo} from 'modules/components/Icon';

import * as api from './api';

export default class Header extends React.Component {
  static propTypes = {
    active: PropTypes.oneOf(['dashboard', 'instances']),
    instances: PropTypes.number,
    filters: PropTypes.number,
    selections: PropTypes.number,
    incidents: PropTypes.number,
    detail: PropTypes.element
  };

  state = {
    forceRedirect: false,
    user: {}
  };

  fetchUser = async () => {
    return await api.user();
  };

  componentDidMount = async () => {
    const user = await this.fetchUser();
    this.setState({user});
  };

  createBadgeEntry = label => {
    const type = label.toLowerCase();
    return (
      <Styled.ListLink active={this.props.active === 'instances'}>
        <Link to="/instances">
          <span>{label}</span>
          <Styled.Badge type={type}>{this.props[type] || 0}</Styled.Badge>
        </Link>
      </Styled.ListLink>
    );
  };

  handleLogout = async () => {
    await api.logout();
    this.setState({forceRedirect: true});
  };

  render() {
    const {active, detail} = this.props;
    const {firstname, lastname} = this.state.user || {};
    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      <Styled.Header>
        <Styled.Dashboard active={active === 'dashboard'}>
          <Link to="/">
            <Logo />
            <span>Dashboard</span>
          </Link>
        </Styled.Dashboard>
        {this.createBadgeEntry('Instances')}
        {this.createBadgeEntry('Filters')}
        {this.createBadgeEntry('Selections')}
        {this.createBadgeEntry('Incidents')}
        <Styled.Detail>{detail}</Styled.Detail>
        <Styled.ProfileDropdown>
          <Dropdown label={`${firstname} ${lastname}`}>
            <Dropdown.Option
              data-test="logout-button"
              onClick={this.handleLogout}
            >
              Logout
            </Dropdown.Option>
          </Dropdown>
        </Styled.ProfileDropdown>
      </Styled.Header>
    );
  }
}
