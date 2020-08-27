/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Redirect} from 'react-router-dom';

import {login} from 'modules/api/login';
import Button from 'modules/components/Button';
import {PAGE_TITLE} from 'modules/constants';
import {clearStateLocally} from 'modules/utils/localStorage';

import {Disclaimer} from './Disclaimer';
import {REQUIRED_FIELD_ERROR, LOGIN_ERROR} from './constants';
import * as Styled from './styled';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';

class Login extends React.Component {
  static propTypes = {
    location: PropTypes.object.isRequired,
  };

  state = {
    username: '',
    password: '',
    forceRedirect: false,
    error: null,
    isLoading: false,
  };

  componentDidMount() {
    document.title = PAGE_TITLE.LOGIN;
  }

  handleLogin = async (e) => {
    e.preventDefault();
    const {username, password} = this.state;

    if (username.length === 0 || password.length === 0) {
      return this.setState({error: REQUIRED_FIELD_ERROR, isLoading: false});
    }

    this.setState({isLoading: true});

    try {
      await login({username, password});
      clearStateLocally();
      this.setState({forceRedirect: true, isLoading: false});
    } catch (e) {
      this.setState({error: LOGIN_ERROR, isLoading: false});
    }
  };

  handleInputChange = ({target: {name, value}}) => {
    this.setState({[name]: value});
  };

  render() {
    const {username, password, forceRedirect, error, isLoading} = this.state;

    // case of successful login
    if (forceRedirect) {
      const locationState = this.props.location.state || {referrer: '/'};
      return (
        <Redirect
          to={{
            pathname: locationState.referrer,
            state: {isLoggedIn: true},
          }}
        />
      );
    }

    // default render
    return (
      <>
        {isLoading && <SpinnerSkeleton data-test="spinner" />}
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
            <Button
              data-test="login-button"
              type="submit"
              size="large"
              title="Log in"
            >
              Log in
            </Button>
          </Styled.LoginForm>
          <Disclaimer {...window.clientConfig} />
          <Styled.Copyright />
        </Styled.Login>
      </>
    );
  }
}

export {Login};
