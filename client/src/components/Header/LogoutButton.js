/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import {get} from 'request';
import {withErrorHandling} from 'HOC';
import {Button} from 'components';
import {addNotification} from 'notifications';

import './LogoutButton.scss';

export default withRouter(
  withErrorHandling(
    class LogoutButton extends React.Component {
      logout = () => {
        this.props.mightFail(
          get('api/authentication/logout'),
          () => {
            // After logging out we want to go to the Overview page '/'.
            // React-Router does not refresh a route if it is the current route
            // so we temporarily go to another route '/logout' to force the reload
            // of the Overview page. Read more here: https://stackoverflow.com/a/51332885
            this.props.history.push('/logout');
            this.props.history.replace('/');
          },
          () => addNotification({text: 'Logout failed.', type: 'error'})
        );
      };

      render() {
        return (
          <div className="LogoutButton">
            <Button onClick={this.logout} title="Log out">
              Logout
            </Button>
          </div>
        );
      }
    }
  )
);
