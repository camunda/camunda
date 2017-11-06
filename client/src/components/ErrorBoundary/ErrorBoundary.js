import React from 'react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      error: null
    };
  }

  componentDidCatch(error) {
    this.setState({error});
  }

  render() {
    if (this.state.error) {
      return (<div>
        <h1>Oh no :(</h1>
        <p>{this.state.error.message}</p>
      </div>);
    }
    return this.props.children;
  }
}
