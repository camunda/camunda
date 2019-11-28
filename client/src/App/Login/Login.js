/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Redirect} from 'react-router-dom';

import {login} from 'modules/api/login';
import withSharedState from 'modules/components/withSharedState';
import Button from 'modules/components/Button';
import {PAGE_TITLE} from 'modules/constants';

import {REQUIRED_FIELD_ERROR, LOGIN_ERROR} from './constants';
import * as Styled from './styled';

class Login extends React.Component {
  static propTypes = {
    location: PropTypes.object.isRequired,
    clearStateLocally: PropTypes.func.isRequired
  };

  state = {
    username: '',
    password: '',
    forceRedirect: false,
    error: null
  };

  componentDidMount() {
    document.title = PAGE_TITLE.LOGIN;
  }

  handleLogin = async e => {
    e.preventDefault();
    const {username, password} = this.state;

    if (username.length === 0 || password.length === 0) {
      return this.setState({error: REQUIRED_FIELD_ERROR});
    }

    try {
      await login({username, password});
      this.props.clearStateLocally();
      this.setState({forceRedirect: true});
    } catch (e) {
      this.setState({error: LOGIN_ERROR});
    }
  };

  handleInputChange = ({target: {name, value}}) => {
    this.setState({[name]: value});
  };

  render() {
    const {username, password, forceRedirect, error} = this.state;

    // case of successful login
    if (forceRedirect) {
      const locationState = this.props.location.state || {referrer: '/'};
      return <Redirect to={locationState.referrer} />;
    }

    // default render
    return (
      <Styled.Login onSubmit={this.handleLogin}>
        <Styled.LoginHeader>
          <Styled.Logo />
          <Styled.LoginTitle>Operate</Styled.LoginTitle>
        </Styled.LoginHeader>
        <Styled.LoginForm>
          <Styled.FormError>{error}</Styled.FormError>
          <Styled.UsernameInput
            value={username}
            name="username"
            type="text"
            onChange={this.handleInputChange}
            placeholder="Username"
            aria-label="User Name"
          />
          <Styled.PasswordInput
            value={password}
            name="password"
            type="password"
            onChange={this.handleInputChange}
            placeholder="Password"
            aria-label="Password"
          />
          <Button type="submit" size="large" title="Log in">
            Log in
          </Button>
        </Styled.LoginForm>
        <Styled.Disclaimer>
          This Camunda Operate distribution is available under an evaluation
          license that is valid for development (non-production) use only. By
          continuing using this software, you agree to the{' '}
          <Styled.Anchor href="https://zeebe.io/legal/operate-evaluation-license">
            Terms and Conditions
          </Styled.Anchor>{' '}
          of the Operate Trial Version and our{' '}
          <Styled.Anchor href="https://zeebe.io/privacy/">
            Privacy Statement
          </Styled.Anchor>
          .
        </Styled.Disclaimer>
        <Styled.Copyright />
      </Styled.Login>
    );
  }
}

export default withSharedState(Login);
