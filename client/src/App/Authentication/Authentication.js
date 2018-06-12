import React from 'react';
import {Redirect} from 'react-router-dom';

import {setResponseInterceptor} from 'modules/request';

class Authentication extends React.Component {
  constructor(props) {
    super(props);
    setResponseInterceptor(this.interceptResponse);
  }

  state = {
    forceRedirect: false
  };

  interceptResponse = ({status}) => {
    if (status === 401) {
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

    // once forceRedirect is set to true, we reset it to false
    // to prevent always redirecting to Login
    forceRedirect && this.setState({forceRedirect: false});
  }
}

export default Authentication;
