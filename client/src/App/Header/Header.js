import React from 'react';
import {Redirect, Link} from 'react-router-dom';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

import Dropdown from 'modules/components/Dropdown';

import * as api from 'modules/api/header';

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

  componentDidMount = async () => {
    const user = await this.fetchUser();
    this.setState({user});
  };

  fetchUser = async () => {
    return await api.fetchUser();
  };

  createBadgeEntry = label => {
    const type = label.toLowerCase();
    const count = this.props[type] || 0;
    const title = `${count} ${label}`;

    return (
      <Styled.ListLink active={this.props.active === 'instances'}>
        <Link to="/instances" title={title}>
          <span>{label}</span>
          <Styled.Badge type={type} badgeContent={count} />
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
        <Styled.Menu role="navigation">
          <li>
            <Styled.Dashboard active={active === 'dashboard'}>
              <Link to="/">
                <Styled.LogoIcon />
                <span>Dashboard</span>
              </Link>
            </Styled.Dashboard>
          </li>
          <li>{this.createBadgeEntry('Instances')}</li>
          <li>{this.createBadgeEntry('Filters')}</li>
          <li>{this.createBadgeEntry('Selections')}</li>
          <li>{this.createBadgeEntry('Incidents')}</li>
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
