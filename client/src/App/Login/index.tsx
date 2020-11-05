/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {login} from 'modules/api/login';
import Button from 'modules/components/Button';
import {PAGE_TITLE} from 'modules/constants';
import {clearStateLocally} from 'modules/utils/localStorage';

import {Disclaimer} from './Disclaimer';
import {REQUIRED_FIELD_ERROR, LOGIN_ERROR} from './constants';
import * as Styled from './styled';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';

type Props = {
  location: any;
};

type State = any;

class Login extends React.Component<Props, State> {
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

  handleLogin = async (e: any) => {
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

  handleInputChange = ({target: {name, value}}: any) => {
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
        {isLoading && <SpinnerSkeleton data-testid="spinner" />}
        <Styled.Login onSubmit={this.handleLogin}>
          <Styled.LoginHeader>
            <Styled.Logo />
            <Styled.LoginTitle>Operate</Styled.LoginTitle>
          </Styled.LoginHeader>
          <Styled.LoginForm>
            <Styled.FormError>{error}</Styled.FormError>
            <Styled.UsernameInput
              // @ts-expect-error ts-migrate(2769) FIXME: Property 'value' does not exist on type 'Intrinsic... Remove this comment to see the full error message
              value={username}
              name="username"
              type="text"
              onChange={this.handleInputChange}
              placeholder="Username"
              aria-label="User Name"
            />
            <Styled.PasswordInput
              // @ts-expect-error ts-migrate(2769) FIXME: Property 'value' does not exist on type 'Intrinsic... Remove this comment to see the full error message
              value={password}
              name="password"
              type="password"
              onChange={this.handleInputChange}
              placeholder="Password"
              aria-label="Password"
            />
            {/* @ts-expect-error ts-migrate(2322) FIXME: Property 'children' does not exist on type 'Intrin... Remove this comment to see the full error message */}
            <Button
              data-testid="login-button"
              type="submit"
              size="large"
              title="Log in"
            >
              Log in
            </Button>
          </Styled.LoginForm>
          {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'clientConfig' does not exist on type 'Wi... Remove this comment to see the full error message */}
          <Disclaimer {...window.clientConfig} />
          <Styled.Copyright />
        </Styled.Login>
      </>
    );
  }
}

export {Login};
