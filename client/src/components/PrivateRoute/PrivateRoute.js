import React from 'react';
import {Route, Redirect} from 'react-router-dom';
import {destroy, getToken} from 'credentials';
import {addHandler, removeHandler} from 'request';
import {themed} from 'theme';

export default themed(
  class PrivateRoute extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        forceRedirect: false
      };
    }

    componentDidMount() {
      addHandler(this.handleResponse);
    }

    handleResponse = response => {
      if (response.status === 401) {
        destroy();
        if (this.props.theme === 'dark') {
          this.props.toggleTheme();
        }
        this.setState({
          forceRedirect: true
        });
      }

      return response;
    };

    render() {
      const {component: Component, ...rest} = this.props;
      return (
        <Route
          {...rest}
          render={props => {
            return getToken() && !this.state.forceRedirect ? (
              <Component {...props} />
            ) : (
              <Redirect
                to={{
                  pathname: '/login',
                  state: {from: props.location}
                }}
              />
            );
          }}
        />
      );
    }

    componentDidUpdate(prevProps, prevState) {
      if (this.state.forceRedirect) {
        this.setState({
          forceRedirect: false
        });
      }
    }

    componentWillUnmount() {
      removeHandler(this.handleResponse);
    }
  }
);
