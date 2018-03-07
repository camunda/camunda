import React from 'react';
import {mount} from 'enzyme';

import Modal from './Modal';

it('should not render anything if the modal is not opened', () => {
  const node = mount(<Modal>ModalContent</Modal>);

  expect(node.html()).toBe(null);
});

// --- INCOMPATIBLE TESTS --- //

// testing this component is currently not possible until a bug in the
// testing framework enzyme regarding Portals is fixed:
//
// https://github.com/airbnb/enzyme/issues/1150

// it('should render basic children', () => {
//   const node = mount(<Modal open={true}>
//     ModalContent
//   </Modal>);

//   expect(node).toIncludeText('ModalContent');
// });

// it('should call the onClose function on backdrop click', () => {
//   const spy = jest.fn();
//   const node = mount(<Modal open={true} onClose={spy}>
//     ModalContent
//   </Modal>);

//   node.simulate('click');

//   expect(spy).toHaveBeenCalled();
// });

// it('should not call the onClose function when modal content is clicked', () => {
//   const spy = jest.fn();
//   const node = mount(<Modal open={true} onClose={spy}>
//     <button>Some button in the modal</button>
//   </Modal>);

//   node.find('button').simulate('click');

//   expect(spy).not.toHaveBeenCalled();
// });

// it('should position the modal in the center of the screen', () => {
//   const node = mount(<Modal open={true}>
//     <div style={{height: '200px'}}/>
//   </Modal>);

//   expect(node.find('.Modal__container')).toHaveStyle('margin-top', '-100px');
// });

// it('should remove the modal from the dom after it is unmounted');

// describe('Header', () => {

//   it('should render children');

//   it('should contain a close button');

//   it('should call the onClose function on close button click');

// });

// describe('Content', () => {
//   it('should render children');
// });

// describe('Actions', () => {
//   it('should render childen');
// });
