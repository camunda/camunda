import React from 'react';
import {Redirect} from 'react-router-dom';
import {getToken} from 'credentials';

import {login} from './service';

import {Message} from 'components';

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
    const locationState = this.props.location && this.props.location.state;

    if(redirect || getToken()) {
      return (
        <Redirect to={(locationState && locationState.from) || '/'} />
      );
    }

    return (
      <form  className='Login'>
        <h2>Login</h2>
        {error ? (<Message type="error" message='Could not log you in. Please check your username and password.'/>) : ('')}
        <div className='Login__row'>
          <label  className='Login__label'>Username</label>
          <input type='text' placeholder='Username' value={username} onChange={this.handleInputChange} type='text' name='username' autoFocus={true} />
        </div>
        <div className='Login__row'>
          <label className='Login__label'>Password</label>
          <input placeholder='Password' value={password} onChange={this.handleInputChange} name='password' type='password' ref={input => this.passwordField = input} />
        </div>
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
