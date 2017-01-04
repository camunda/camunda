import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/processDisplay.component';

describe('<ProcessDisplay>', () => {
  let Filters;
  let Diagram;
  let node;

  beforeEach(() => {
    Filters = createMockComponent('Filters');
    __set__('Filters', Filters);

    Diagram = createMockComponent('Diagram');
    __set__('Diagram', Diagram);

    ({node} = mountTemplate(<ProcessDisplay selector="processDisplay"/>));
  });

  afterEach(() => {
    __ResetDependency__('Filters');
    __ResetDependency__('Diagram');
  });

  it('should display <Filters> component', () => {
    expect(node).to.contain.text('Filters');
  });

  it('should display <Diagram> component', () => {
    expect(node).to.contain.text('Diagram');
  });
});
