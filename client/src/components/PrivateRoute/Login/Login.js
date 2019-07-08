/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {login} from './service';

import {Logo, Message, Button, Input, Labeled} from 'components';

import './Login.scss';

export default class Login extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      username: '',
      password: '',
      waitingForServer: false,
      error: null
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

    this.setState({waitingForServer: true});

    const authResult = await login(username, password);

    this.setState({waitingForServer: false});

    if (authResult.token) {
      this.props.onLogin(authResult.token);
    } else {
      const error = authResult.errorMessage || 'An error occurred. Could not log you in.';
      this.setState({error});
      this.passwordField.focus();
      this.passwordField.select();
    }
  };

  render() {
    const {username, password, waitingForServer, error} = this.state;

    return (
      <form className="Login">
        <h1>
          <Logo />
          Camunda Optimize
        </h1>
        {error ? <Message type="error">{error}</Message> : ''}
        <div className="controls">
          <div className="row">
            <Labeled label="Username">
              <Input
                type="text"
                placeholder="Username"
                value={username}
                onChange={this.handleInputChange}
                name="username"
                autoFocus={true}
              />
            </Labeled>
          </div>
          <div className="row">
            <Labeled label="Password">
              <Input
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
        <Button
          type="submit"
          onClick={this.submit}
          disabled={waitingForServer}
          variant="primary"
          color="blue"
        >
          Login
        </Button>
      </form>
    );
  }
}
