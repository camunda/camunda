import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {CreateFilter, __set__, __ResetDependency__} from 'main/processDisplay/controls/filter/CreateFilter';

describe('<CreateFilter>', () => {
  let node;
  let Dropdown;
  let Modal;
  let createModal;

  beforeEach(() => {
    Dropdown = createMockComponent('Dropdown');
    __set__('Dropdown', Dropdown);

    Modal = createMockComponent('Modal');
    createModal = sinon.stub().returns(Modal);
    __set__('createModal', createModal);

    ({node} = mountTemplate(<CreateFilter />));
  });

  afterEach(() => {
    __ResetDependency__('Dropdown');
    __ResetDependency__('createModal');
  });

  it('should contain a dropdown', () => {
    expect(node.textContent).to.contain('Dropdown');
  });

  it('should contain a modal', () => {
    expect(node.textContent).to.contain('Modal');
  });
});
