import React from 'react';
import {Redirect} from 'react-router-dom';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

import {Badge, Dropdown} from 'components';
import * as api from './api';

export default class Header extends React.Component {
  static propTypes = {
    active: PropTypes.oneOf(['dashboard', 'instances', 'detail']).isRequired,
    instances: PropTypes.number,
    filters: PropTypes.number,
    selections: PropTypes.number,
    incidents: PropTypes.number,
    detail: PropTypes.string
  };

  state = {
    forceRedirect: false,
    user: {}
  };

  componentDidMount = async () => {
    const user = await api.user();
    this.setState({user});
  };

  createBadgeEntry = label => {
    const type = label.toLowerCase();
    return (
      <Styled.ListLink active={this.props.active === 'instances'}>
        <span>{label}</span>
        <Badge type={type}>{this.props[type]}</Badge>
      </Styled.ListLink>
    );
  };

  logout = async () => {
    await api.logout();
    this.setState({forceRedirect: true});
  };

  render() {
    const {active, detail, ...props} = this.props;
    const {firstname, lastname} = this.state.user || {};
    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      <Styled.Header>
        <Styled.DashboardLink active={active === 'dashboard'}>
          Dashboard
        </Styled.DashboardLink>
        {this.createBadgeEntry('Instances')}
        {props.filters > 0 && this.createBadgeEntry('Filters')}
        {props.selections > 0 && this.createBadgeEntry('Selections')}
        {props.incidents > 0 && this.createBadgeEntry('Incidents')}
        {detail && <Styled.Detail>Instance {detail}</Styled.Detail>}
        <Styled.ProfileDropdown>
          <Dropdown label={`${firstname} ${lastname}`}>
            <Dropdown.Option onClick={this.logout}>Logout</Dropdown.Option>
          </Dropdown>
        </Styled.ProfileDropdown>
      </Styled.Header>
    );
  }
}
