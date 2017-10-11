import React from 'react';
import {getRouter} from 'router';
import {getLogin} from 'login';
import {performLogin} from './service';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;
const router = getRouter();

export class LoginFormReact extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      user: '',
      password: ''
    };
    this.focusRequired = false;
  }

  render() {
    const {inProgress, error} = this.props;
    const errorMsg = <div className="col-sm-offset-2 col-sm-10 text-danger">
      Could not login. Check username / password.
    </div>;
    const spinner = <span className="glyphicon glyphicon-refresh spin"></span>;

    return <form className="form-horizontal form-signin" onSubmit={this.submit}>
      <h2>Login</h2>
      <div className="form-group">
        <label className="col-sm-2 control-label">User</label>
        <div className="col-sm-10">
          <input type="text"
                 value={this.state.user}
                 className="form-control user"
                 placeholder="Username"
                 onChange={this.changeUser}
                 disabled={inProgress} />
        </div>
      </div>
      <div className="form-group">
        <label className="col-sm-2 control-label">Password</label>
        <div className="col-sm-10" >
          <input type="password"
                 value={this.state.password}
                 ref={this.savePasswordField}
                 className="form-control password"
                 placeholder="Password"
                 onChange={this.changePassword}
                 disabled={inProgress} />
        </div>
      </div>
      <div className="form-group">
        {error ? errorMsg : ''}
      </div>
      <div className="form-group">
        <div className="col-sm-offset-2 col-sm-10">
          <button type="submit" className="btn btn-primary" disabled={inProgress}>
            {inProgress ? spinner : 'Login'}
          </button>
        </div>
      </div>
    </form>;
  }

  componentDidMount() {
    if (getLogin()) {
      router.goTo('default');
    }
  }

  componentWillReceiveProps({error}) {
    if (error) {
      this.focusRequired = true;
    }
  }

  componentDidUpdate() {
    if (this.focusRequired) {
      this.passwordField.focus();
      this.passwordField.select();

      this.focusRequired = false;
    }
  }

  savePasswordField = input => this.passwordField = input;

  changeUser = event => this.setState({
    user: event.target.value
  });

  changePassword = event => this.setState({
    password: event.target.value
  });

  submit = event => {
    const {user, password} = this.state;

    event.preventDefault();

    performLogin(user, password);
  }
}

export const LoginForm = createViewUtilsComponentFromReact('div', LoginFormReact);
