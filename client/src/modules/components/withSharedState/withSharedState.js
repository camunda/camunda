import React from 'react';

export default function withSharedState(Component) {
  class WithSharedState extends React.Component {
    storeStateLocally = state => {
      const current = JSON.parse(localStorage.getItem('sharedState') || '{}');

      localStorage.setItem(
        'sharedState',
        JSON.stringify({...current, ...state})
      );
    };

    clearStateLocally = () => {
      localStorage.removeItem('sharedState');
    };

    getStateLocally = () => {
      return JSON.parse(localStorage.getItem('sharedState') || '{}');
    };

    render() {
      return (
        <Component
          storeStateLocally={this.storeStateLocally}
          getStateLocally={this.getStateLocally}
          clearStateLocally={this.clearStateLocally}
          {...this.props}
        />
      );
    }
  }

  WithSharedState.displayName = `${Component.displayName ||
    Component.name ||
    'Component'}SharedState`;

  return WithSharedState;
}
