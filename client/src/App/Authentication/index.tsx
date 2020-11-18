/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect, withRouter} from 'react-router-dom';

import {get, setResponseInterceptor} from 'modules/request';

type Props = {
  location: {
    pathname: string;
    state?: {
      isLoggedIn?: boolean;
    };
  };
};

type State = any;

class Authentication extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);

    // forceRedirect === null indicates the login status was not checked yet
    this.state = {
      forceRedirect: null,
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
      .then((response) => response.status)
      .catch((error) => error.status);
  };

  checkLoginStatus = (status: any) => {
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
        forceRedirect: true,
      },
      this.disableForceRedirect
    );
  };

  disableForceRedirect = () => {
    this.setState({
      forceRedirect: false,
    });
  };

  interceptResponse = ({status}: any) => {
    if (status === 401) {
      this.enableForceRedirect();
    }
  };

  render() {
    const {state} = this.props.location;
    if (state && state.isLoggedIn) {
      return this.props.children;
    } else if (this.state.forceRedirect === null) {
      // show empty page until we know if we need to redirect to login screen
      return null;
    } else if (this.state.forceRedirect) {
      return (
        <Redirect
          to={{
            pathname: '/login',
            state: {referrer: this.props.location.pathname},
          }}
          push={true}
        />
      );
    } else {
      return this.props.children;
    }
  }
}

// @ts-expect-error ts-migrate(2345) FIXME: Type 'unknown' is not assignable to type '{ isLogg... Remove this comment to see the full error message
export default withRouter(Authentication);
