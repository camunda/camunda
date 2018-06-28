import React from 'react';
import PropTypes from 'prop-types';

const {Consumer, Provider} = React.createContext();

export const ExpandConsumer = Consumer;

export class ExpandProvider extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ]).isRequired
  };

  state = {
    expandedId: null
  };

  expand = id => {
    this.setState({expandedId: id});
  };

  resetExpanded = () => {
    this.setState({expandedId: null});
  };

  render() {
    const contextValue = {
      ...this.state,
      expand: this.expand,
      resetExpanded: this.resetExpanded
    };
    return <Provider value={contextValue}>{this.props.children}</Provider>;
  }
}

export const withExpand = Component => {
  function WithExpand(props) {
    return (
      <ExpandConsumer>
        {context => <Component {...props} {...context} />}
      </ExpandConsumer>
    );
  }

  WithExpand.WrappedComponent = Component;

  WithExpand.displayName = `WithExpand(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithExpand;
};
