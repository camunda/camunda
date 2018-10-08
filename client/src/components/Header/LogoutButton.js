import React from 'react';
import {Redirect} from 'react-router-dom';
import {destroy} from 'credentials';
import {get} from 'request';
import {withErrorHandling} from 'HOC';
import {Button} from 'components';

import './LogoutButton.css';

export default withErrorHandling(
  class LogoutButton extends React.Component {
    state = {
      redirect: false
    };

    logout = () => {
      this.props.mightFail(
        get('api/authentication/logout'),
        () => {
          destroy();
          this.setState({
            redirect: true
          });
        },
        error => {
          alert(`Unable to logout: ${error.statusText}`);
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
