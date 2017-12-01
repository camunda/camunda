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
    const {error} = this.state;

    if (error) {
      return (<div>
        <h1>Oh no :(</h1>
        <pre>{error.message || error}</pre>
      </div>);
    }
    return this.props.children;
  }
}
