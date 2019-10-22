/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Redirect, withRouter} from 'react-router-dom';

import {get, setResponseInterceptor} from 'modules/request';

class Authentication extends React.Component {
  static propTypes = {
    location: PropTypes.shape({
      pathname: PropTypes.string.isRequired
    }).isRequired,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  constructor(props) {
    super(props);

    // forceRedirect === null indicates the login status was not checked yet
    this.state = {
      forceRedirect: null
    };
  }

  componentDidMount() {
    this.requestUserEndpoint().then(this.checkLoginStatus);
  }

  componentWillUnmount() {
    setResponseInterceptor(null);
  }

  requestUserEndpoint = () => {
    // use user endpoint to check for authentication
    return get('/api/authentications/user')
      .then(response => response.status)
      .catch(error => error.status);
  };

  checkLoginStatus = status => {
    // intercept further responses to check if we get logged out
    setResponseInterceptor(this.interceptResponse);

    if (status === 401) {
      this.enableForceRedirect();
    } else {
      this.disableForceRedirect();
    }
  };

  enableForceRedirect = () => {
    // redirect to login then make sure to reset the state
    // in order to be able to render the children (i.e. the Routes)
    this.setState(
      {
        forceRedirect: true
      },
      this.disableForceRedirect
    );
  };

  disableForceRedirect = () => {
    this.setState({
      forceRedirect: false
    });
  };

  interceptResponse = ({status}) => {
    if (status === 401) {
      this.enableForceRedirect();
    }
  };

  render() {
    if (this.state.forceRedirect === null) {
      // show empty page until we know if we need to redirect to login screen
      return null;
    } else if (this.state.forceRedirect) {
      return (
        <Redirect
          to={{
            pathname: '/login',
            state: {referrer: this.props.location.pathname}
          }}
          push={true}
        />
      );
    } else {
      return this.props.children;
    }
  }
}

export default withRouter(Authentication);
