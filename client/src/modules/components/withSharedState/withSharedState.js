import React from 'react';

export default function withSharedState(Component) {
  class WithSharedState extends React.Component {
    storeState = state => {
      const current = JSON.parse(localStorage.getItem('sharedState') || '{}');

      localStorage.setItem(
        'sharedState',
        JSON.stringify({...current, ...state})
      );
    };

    clearState = () => {
      localStorage.removeItem('sharedState');
    };

    getState = () => {
      return JSON.parse(localStorage.getItem('sharedState') || '{}');
    };

    render() {
      return (
        <Component
          storeState={this.storeState}
          getState={this.getState}
          clearState={this.clearState}
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
