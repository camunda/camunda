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
  state = {isFiltersCollapsed: false, isSelectionsCollapsed: true};

  toggle = target =>
    this.setState(prevState => {
      return {[target]: !prevState[target]};
    });

  toggleFilters = () => this.toggle('isFiltersCollapsed');

  toggleSelections = () => this.toggle('isSelectionsCollapsed');

  expand = target => this.setState({[target]: false});

  expandFilters = () => this.expand('isFiltersCollapsed');

  expandSelections = () => this.expand('isSelectionsCollapsed');

  expandFilters = () => {
    this.setState({isFiltersCollapsed: false});
  };

  expandSelections = () => {
    this.setState({isSelectionsCollapsed: false});
  };

  render() {
    const contextValue = {
      ...this.state,
      toggleFilters: this.toggleFilters,
      toggleSelections: this.toggleSelections,
      expandFilters: this.expandFilters,
      expandSelections: this.expandSelections
    };

    return (
      <CollapsablePanelContext.Provider value={contextValue}>
        {this.props.children}
      </CollapsablePanelContext.Provider>
    );
  }
}

const withCollapsablePanel = Component => {
  function WithCollapsablePanel(props) {
    return (
      <CollapsablePanelConsumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </CollapsablePanelConsumer>
    );
  }

  WithCollapsablePanel.WrappedComponent = Component;

  WithCollapsablePanel.displayName = `WithCollapsablePanel(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithCollapsablePanel;
};

export {
  CollapsablePanelConsumer,
  CollapsablePanelProvider,
  withCollapsablePanel
};
