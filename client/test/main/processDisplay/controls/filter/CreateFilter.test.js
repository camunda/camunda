import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import {CreateFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/CreateFilter';

describe('<CreateFilter>', () => {
  let node;
  let Dropdown;
  let Modal;

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown');
    __set__('Dropdown', Dropdown);

    Modal = createMockComponent('Modal');
    __set__('Modal', Modal);

    ({node} = mountTemplate(<CreateFilter />));
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('Modal');
  });

  it('should contain a dropdown', () => {
    expect(node.textContent).to.contain('Dropdown');
  });

  it('should contain a modal', () => {
    expect(node.textContent).to.contain('Modal');
  });
});
