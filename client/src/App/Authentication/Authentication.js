import React from 'react';
import PropTypes from 'prop-types';
import {Redirect, withRouter} from 'react-router-dom';

import {setResponseInterceptor} from 'modules/request';

class Authentication extends React.Component {
  constructor(props) {
    super(props);
    setResponseInterceptor(this.interceptResponse);
  }

  static propTypes = {
    location: PropTypes.shape({
      pathname: PropTypes.string.isRequired
    }).isRequired
  };

  defaultState = {forceRedirect: false};

  state = {...this.defaultState};

  resetState = () => {
    this.setState(this.defaultState);
  };

  interceptResponse = ({status}) => {
    if (status === 401) {
      // redirect to login then make sure to reset the state
      // in order to be able to render the children (i.e. the Routes)
      this.setState(
        {
          forceRedirect: true
        },
        this.resetState
      );
    }
  };

  render() {
    return this.state.forceRedirect ? (
      <Redirect
        to={{
          pathname: '/login',
          state: {referrer: this.props.location.pathname}
        }}
        push={true}
      />
    ) : (
      this.props.children
    );
  }
}

export default withRouter(Authentication);
