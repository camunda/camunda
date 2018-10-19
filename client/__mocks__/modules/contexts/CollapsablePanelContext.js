const context = {
  filters: false,
  selections: false,
  toggleFilters: jest.fn(),
  toggleSelections: jest.fn()
};

const CollapsablePanelConsumer = props => props.children(context);
const withCollapsablePanel = Component => Component;

export {CollapsablePanelConsumer, withCollapsablePanel};
