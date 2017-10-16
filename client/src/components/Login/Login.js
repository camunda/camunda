import React from 'react';
import {Redirect} from 'react-router-dom';
import {getToken} from 'credentials';

import {login} from './service';
import './Login.css';

export default class Login extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      username: '',
      password: '',
      error: false,
      redirect: false
    };
  }

  handleInputChange = ({target: {name, value}}) => {
    this.setState({
      [name]: value,
      error: false
    });
  }

  submit = async evt => {
    evt.preventDefault();

    const {username, password} = this.state;

    const token = await login(username, password);

    if(token) {
      this.setState({redirect: true});
    } else {
      this.setState({error: true});
    }
  }

  render() {
    const {username, password, redirect, error} = this.state;
    const locationState = this.props.location.state;

    if(redirect || getToken()) {
      return (
        <Redirect to={(locationState && locationState.from) || '/'} />
      );
    }

    return (
      <form className='Login'>
        <h2>Login</h2>
        <div>
          <label>Username</label>
          <input placeholder='Username' value={username} onChange={this.handleInputChange} type='text' name='username' autoFocus={true} />
        </div>
        <div>
          <label>Password</label>
          <input placeholder='Password' value={password} onChange={this.handleInputChange} name='password' type='password' ref={input => this.passwordField = input} />
        </div>
        <div className={`error-message${error ? '' : ' hidden'}`}>Could not login. Check username / password.</div>
        <button type='submit' onClick={this.submit}>Login</button>
      </form>
    );
  }

  componentDidUpdate() {
    if(this.state.error) {
      this.passwordField.focus();
      this.passwordField.select();
    }
  }
}