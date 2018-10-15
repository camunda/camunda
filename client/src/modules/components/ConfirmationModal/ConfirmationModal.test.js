import React from 'react';
import {mount} from 'enzyme';

import ConfirmationModal from './ConfirmationModal';

it('should have a closed modal when isVisible is false', () => {
  const props = {
    isVisible: false,
    entityName: '',
    confirmModal: () => {},
    closeModal: () => {}
  };

  const node = mount(<ConfirmationModal {...props} />);
  expect(node.find('Modal').props().open).toBeFalsy();
});

it('should show the name of the Entity to delete', () => {
  const props = {
    isVisible: true,
    entityName: 'test',
    confirmModal: () => {},
    closeModal: () => {}
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node).toIncludeText('test');
  expect(node.find('Modal').props().open).toBeTruthy();
});

it('should invok closeModal when cancel button is clicked', async () => {
  const spy = jest.fn();
  const props = {
    isVisible: true,
    entityName: 'test',
    confirmModal: () => {},
    closeModal: spy
  };
  const node = mount(<ConfirmationModal {...props} />);

  node.find('Button.CloseModalButton').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should invok confirmModal when confirm button is clicked', async () => {
  const spy = jest.fn();
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    isVisible: true,
    entityName: testEntity.name,
    confirmModal: spy,
    closeModal: () => {}
  };
  const node = mount(<ConfirmationModal {...props} />);

  node.find('Button.ConfirmModalButton').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should show default operation text if conflict is not set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    isVisible: true,
    entityName: testEntity.name,
    confirmModal: () => {},
    closeModal: () => {},
    defaultOperation: 'Delete'
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node.find('Button.ConfirmModalButton')).toIncludeText('Delete');
});

it('should show conflict information if conflict prop is set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    isVisible: true,
    entityName: testEntity.name,
    confirmModal: () => {},
    closeModal: () => {},
    conflict: {type: 'Save', items: [{id: '1', name: 'testAlert', type: 'entityType'}]},
    defaultOperation: 'Delete'
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node.find('li')).toIncludeText('testAlert');
  expect(node.find('Button.ConfirmModalButton')).toIncludeText('Save');
});
