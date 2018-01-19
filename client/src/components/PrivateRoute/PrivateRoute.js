import React from 'react';
import {Route, Redirect} from 'react-router-dom'
import {destroy, getToken} from 'credentials';
import {addHandler, removeHandler} from 'request';

export default class PrivateRoute extends React.Component {
  constructor(props) {
    super(props);

    addHandler(this.handleResponse);

    this.state = {
      forceRedirect: false
    };
  }

  handleResponse = response => {
    if(response.status === 401) {
      destroy();
      this.setState({
        forceRedirect: true
      });
    }

    return response;
  }

  render() {
    const {component: Component, ...rest} = this.props;
    return (<Route {...rest} render={props => {
      return ((getToken() && !this.state.forceRedirect) ? (
        <Component {...props} />
      ) : (
        <Redirect to={{
          pathname: '/login',
          state: { from: props.location }
        }} />
      )
    )}} />);
  }

  componentDidUpdate(prevProps, prevState) {
    if(this.state.forceRedirect) {
      this.setState({
        forceRedirect: false
      });
    }
  }

  componentWillUnmount() {
    removeHandler(this.handleResponse);
  }
}
