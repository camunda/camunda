import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/processDisplay.component';

describe('<ProcessDisplay>', () => {
  let Filters;
  let node;

  beforeEach(() => {
    Filters = createMockComponent('Filters');
    __set__('Filters', Filters);

    ({node} = mountTemplate(<ProcessDisplay/>));
  });

  afterEach(() => {
    __ResetDependency__('Filters');
  });

  it('should display <Filters> component', () => {
    expect(node).to.contain.text('Filters');
  });
});
