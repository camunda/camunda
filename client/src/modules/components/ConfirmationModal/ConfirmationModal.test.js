import React from 'react';
import {mount} from 'enzyme';

import ConfirmationModal from './ConfirmationModal';

jest.mock('components', () => {
  const Button = props => (
    <button {...props} active={props.active ? 'true' : undefined}>
      {props.children}
    </button>
  );

  const Modal = ({onConfirm, ...props}) => <div {...props}>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  return {Button, Modal};
});

it('should have a closed modal when open is false', () => {
  const props = {
    open: false,
    entityName: '',
    onConfirm: () => {},
    onClose: () => {}
  };

  const node = mount(<ConfirmationModal {...props} />);
  expect(node.find('Modal').props().open).toBeFalsy();
});

it('should show the name of the Entity to delete', () => {
  const props = {
    open: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: () => {}
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node).toIncludeText('test');
  expect(node.find('Modal').props().open).toBeTruthy();
});

it('should invok onClose when cancel button is clicked', async () => {
  const spy = jest.fn();
  const props = {
    open: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: spy
  };
  const node = mount(<ConfirmationModal {...props} />);

  node.find('Button.close').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should invok confirmModal when confirm button is clicked', async () => {
  const spy = jest.fn();
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: spy,
    onClose: () => {}
  };
  const node = mount(<ConfirmationModal {...props} />);

  node.find('Button.confirm').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should show default operation text if conflict is not set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: () => {},
    onClose: () => {}
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node.find('Button.confirm')).toIncludeText('Delete');
});

it('should show conflict information if conflict prop is set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: () => {},
    onClose: () => {},
    conflict: {type: 'Save', items: [{id: '1', name: 'testAlert', type: 'entityType'}]}
  };
  const node = mount(<ConfirmationModal {...props} />);

  expect(node.find('li')).toIncludeText('testAlert');
  expect(node.find('Button.confirm')).toIncludeText('Save');
});
