import {CollapsablePanelProvider} from './CollapsablePanelContext';

describe('CollapsablePanelProvider', () => {
  it('should store default panels collabsable states', () => {
    const node = new CollapsablePanelProvider();

    // Filters panel is not collapsed
    expect(node.state.filters).toBe(false);

    // Selections panel is not collapsed
    expect(node.state.selections).toBe(false);
  });
});
