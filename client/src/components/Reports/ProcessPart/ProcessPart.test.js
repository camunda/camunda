import React from 'react';

import ProcessPart from './ProcessPart';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Button: props => <button {...props} />,
    BPMNDiagram: () => <div>BPMNDiagram</div>,
    ClickBehavior: () => <div>ClickBehavior</div>,
    ActionItem: ({onClick, children}) => (
      <div>
        <button className="clearBtn" onClick={onClick}>
          X
        </button>
        {children}
      </div>
    )
  };
});

it('should display a button if no process part is set', () => {
  const node = mount(<ProcessPart />);

  expect(
    node.findWhere(n => n.type() === 'button' && n.text() === 'Process Instance Part')
  ).toBePresent();
});

it('should not display the button is process part is set', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  expect(
    node.findWhere(n => n.type() === 'button' && n.text() === 'Process Instance Part')
  ).not.toBePresent();
});

it('should show a preview of the process part', () => {
  const node = mount(
    <ProcessPart
      processPart={{start: 'a', end: 'b'}}
      flowNodeNames={{a: 'Start Node', b: 'End Node'}}
    />
  );

  expect(node).toIncludeText('Only regard part between Start Node and End Node');
});

it('should remove the process part', () => {
  const spy = jest.fn();
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} update={spy} />);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalledWith(null);
});

it('should open a modal when clicking the button', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('.ProcessPart__current').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('should show the bpmn diagram', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('.ProcessPart__current').simulate('click');

  expect(node.find('BPMNDiagram')).toBePresent();
});
