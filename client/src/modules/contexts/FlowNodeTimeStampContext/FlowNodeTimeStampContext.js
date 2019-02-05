import React from 'react';
import PropTypes from 'prop-types';

// default value?
const FlowNodeTimeStampContext = React.createContext();

export const FlowNodeTimeStampConsumer = FlowNodeTimeStampContext.Consumer;

export class FlowNodeTimeStampProvider extends React.PureComponent {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  state = {
    showTimeStamp: false
  };

  handleTimeStampToggle = () => {
    this.setState(prevState => {
      return {
        showTimeStamp: !prevState.showTimeStamp
      };
    });
  };

  render() {
    const contextValue = {
      ...this.state,
      onTimeStampToggle: this.handleTimeStampToggle
    };

    return (
      <FlowNodeTimeStampContext.Provider value={contextValue}>
        {this.props.children}
      </FlowNodeTimeStampContext.Provider>
    );
  }
}

export const withFlowNodeTimeStampContext = Component => {
  function withFlowNodeTimeStampContext(props) {
    return (
      <FlowNodeTimeStampConsumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </FlowNodeTimeStampConsumer>
    );
  }

  withFlowNodeTimeStampContext.WrappedComponent = Component;

  withFlowNodeTimeStampContext.displayName = `withFlowNodeTimeStampContext(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return withFlowNodeTimeStampContext;
};
