import React from 'react';
import {mount} from 'enzyme';

import DeleteModal from './DeleteModal';

it('should have a closed modal when deleteModalVisible is false', () => {
  const props = {
    deleteModalVisible: false,
    deleteModalEntity: {},
    deleteEntity: () => {},
    closeDeleteModal: () => {}
  };
  const node = mount(<DeleteModal {...props} />);
  expect(node.find('Modal').props().open).toBeFalsy();
});

it('should show the name of the Entity to delete', () => {
  const props = {
    isVisible: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: () => {}
  };
  const node = mount(<DeleteModal {...props} />);

  expect(node).toIncludeText('test');
  expect(node.find('Modal').props().open).toBeTruthy();
});

it('should invok closeDeleteModal when cancel button is clicked', async () => {
  const spy = jest.fn();
  const props = {
    isVisible: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: spy
  };
  const node = mount(<DeleteModal {...props} />);

  node.find('Button.deleteModalButton').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should invok deleteEntity when confirm button is clicked', async () => {
  const spy = jest.fn();
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    isVisible: true,
    entityName: testEntity.name,
    onConfirm: spy,
    onClose: () => {}
  };
  const node = mount(<DeleteModal {...props} />);

  node.find('Button.deleteEntityModalButton').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});
