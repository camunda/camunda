/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {get} from 'request';
import {withErrorHandling} from 'HOC';
import {Button} from 'components';
import {addNotification} from 'notifications';

import './LogoutButton.scss';

export default withErrorHandling(
  class LogoutButton extends React.Component {
    state = {
      redirect: false
    };

    logout = () => {
      this.props.mightFail(
        get('api/authentication/logout'),
        () => {
          this.setState({
            redirect: true
          });
        },
        error => {
          addNotification({text: 'Logout failed.', type: 'error'});
        }
      );
    };

    render() {
      return (
        <div className="LogoutButton">
          <Button onClick={this.logout} title="Log out">
            Logout
          </Button>
          {this.state.redirect && (
            <Redirect
              to={{
                pathname: '/login'
              }}
            />
          )}
        </div>
      );
    }
  }
);
