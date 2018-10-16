import React from 'react';
import PropTypes from 'prop-types';

const CollapsablePanelContext = React.createContext();

// Wrapper that passes the theme as a prop
const CollapsablePanelConsumer = CollapsablePanelContext.Consumer;

// Top level component to pass down theme in the App
class CollapsablePanelProvider extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  // we start with both panels not collapsed
  state = {filters: false, selections: false};

  toggleFilters = () => {
    this.setState(prevState => {
      return {filters: !prevState.filters};
    });
  };

  toggleSelections = () => {
    this.setState(prevState => {
      return {selections: !prevState.selections};
    });
  };

  render() {
    const contextValue = {
      ...this.state,
      toggleFilters: this.toggleFilters,
      toggleSelections: this.toggleSelections
    };

    return (
      <CollapsablePanelContext.Provider value={contextValue}>
        {this.props.children}
      </CollapsablePanelContext.Provider>
    );
  }
}

export {CollapsablePanelConsumer, CollapsablePanelProvider};
