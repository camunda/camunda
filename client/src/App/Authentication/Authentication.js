import React from 'react';
import {Redirect} from 'react-router-dom';

import {setResponseInterceptor} from 'request';

class Authentication extends React.Component {
  constructor(props) {
    super(props);
    setResponseInterceptor(this.interceptResponse);
  }

  state = {
    forceRedirect: false
  };

  interceptResponse = response => {
    if (response.status === 401) {
      // redirect to login
      this.setState({
        forceRedirect: true
      });
    }
  };

  render() {
    return this.state.forceRedirect ? (
      <Redirect to="/login" />
    ) : (
      this.props.children
    );
  }

  componentDidUpdate() {
    const {forceRedirect} = this.state;
    forceRedirect && this.setState({forceRedirect: false});
  }
}

export default Authentication;
