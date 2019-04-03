/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {login} from './service';

import {Logo, Message, Button, Input, Labeled} from 'components';

import './Login.scss';

export default class Login extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      username: '',
      password: '',
      error: null,
      redirect: false
    };
  }

  handleInputChange = ({target: {name, value}}) => {
    this.setState({
      [name]: value
    });
  };

  submit = async evt => {
    evt.preventDefault();

    const {username, password} = this.state;

    const authResult = await login(username, password);

    if (authResult.token) {
      this.setState({error: null, redirect: true});
    } else {
      const error = authResult.errorMessage || 'An error occurred. Could not log you in.';
      this.setState({error});
      this.passwordField.focus();
      this.passwordField.select();
    }
  };

  render() {
    const {username, password, redirect, error} = this.state;
    const locationState = this.props.location && this.props.location.state;

    if (redirect) {
      return <Redirect to={(locationState && locationState.from) || '/'} />;
    }

    return (
      <form className="Login">
        <h1 className="Login__heading">
          <Logo className="Login__logo" />
          Camunda Optimize
        </h1>
        {error ? <Message type="error">{error}</Message> : ''}
        <div className="Login__controls">
          <div className="Login__row">
            <Labeled label="Username">
              <Input
                className="Login__input"
                type="text"
                placeholder="Username"
                value={username}
                onChange={this.handleInputChange}
                name="username"
                autoFocus={true}
              />
            </Labeled>
          </div>
          <div className="Login__row">
            <Labeled label="Password">
              <Input
                className="Login__input"
                placeholder="Password"
                value={password}
                onChange={this.handleInputChange}
                type="password"
                name="password"
                ref={input => (this.passwordField = input)}
              />
            </Labeled>
          </div>
        </div>
        <Button onClick={this.submit} type="primary" color="blue" className="Login__button">
          Login
        </Button>
      </form>
    );
  }
}
