import React, {Component} from 'react';

import {Input} from 'components';

class Dashboard extends Component {
  state = {
    username: '',
    password: ''
  };

  render() {
    return (
      <form>
        <h1>Camunda Operate</h1>
        <Input
          value={this.state.username}
          onChange={({target: {value}}) => this.setState({username: value})}
          placeholder="Username"
        />
        <Input
          value={this.state.password}
          type="password"
          onChange={({target: {value}}) => this.setState({password: value})}
          placeholder="Password"
        />
        <Input
          type="submit"
          onClick={() => alert('login successful')}
          placeholder="Password"
        />
      </form>
    );
  }
}

export default Dashboard;
