import React from 'react';

const jsx = React.createElement;

/**
 * Creates new component with intial state and setProperty method on Component object that can
 * be used to change that property.
 *
 * Child Component passed as second argument to this function will be called with properties passed to created Component and
 * state (passed as properties).
 *
 * setProperties funcion will also be passed as property to child Component.
 *
 * @param {Object} initialState
 * @param {React.Component} Component
 *
 * @return {React.Component}
 */
export function withState(initialState, Component) {
  let _setProperty;

  class WrappedComponent extends React.Component {
    constructor(props) {
      super(props);

      this.state = {...initialState};

      _setProperty = this.setProperty;
    }

    setProperty = (name, value) => {
      this.setState({
        ...this.state,
        [name]: value
      });
    }

    render() {
      return <Component {...this.props} {...this.state} setProperty={this.setProperty} />;
    }
  }

  WrappedComponent.setProperty = (name, value) => {
    if (typeof _setProperty === 'function') {
      _setProperty(name, value);
    }
  };

  return WrappedComponent;
}
