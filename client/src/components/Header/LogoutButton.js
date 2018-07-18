import React from 'react';
import {Redirect} from 'react-router-dom';
import {destroy} from 'credentials';
import {get} from 'request';
import {withErrorHandling} from 'HOC';

import './LogoutButton.css';

export default withErrorHandling(
  class LogoutButton extends React.Component {
    state = {
      redirect: false
    };

    logout = () => {
      this.props.mightFail(
        get('/api/authentication/logout'),
        () => {
          destroy();
          this.setState({
            redirect: true
          });
        },
        () => {
          alert('Could not logout');
        }
      );
    };

    render() {
      return (
        <div className="LogoutButton">
          <a onClick={this.logout} title="Log out">
            Logout
          </a>
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
