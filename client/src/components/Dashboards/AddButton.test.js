import React from 'react';
import {mount} from 'enzyme';

import AddButton from './AddButton';
import {loadReports} from './service';

jest.mock('components', () => {
  const Modal = props => <div id='Modal'>{props.open && props.children}</div>;
  Modal.Header = props => <div id='modal_header'>{props.children}</div>;
  Modal.Content = props => <div id='modal_content'>{props.children}</div>;
  Modal.Actions = props => <div id='modal_actions'>{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Select,
    Button: props => <button {...props}>{props.children}</button>
  };
});

jest.mock('./service', () => {return {
  loadReports: jest.fn().mockReturnValue([])
}});

it('should load the available reports', () => {
  mount(<AddButton />);

  expect(loadReports).toHaveBeenCalled();
});

it('should open a modal on click', () => {
  const node = mount(<AddButton />);

  node.find('.AddButton').simulate('click');

  expect(node.find('#modal_header')).toIncludeText('Add a Report');
});

it('should render a select element with the available reports as options', () => {
  const node = mount(<AddButton />);

  node.setState({
    modalOpen: true,
    availableReports: [{
      id: 'a', name: 'Report A'
      },{
      id: 'b', name: 'Report B'
    }]
  });

  expect(node.find('select')).toBePresent();
  expect(node.find('select')).toIncludeText('Report A');
  expect(node.find('select')).toIncludeText('Report B');
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = mount(<AddButton addReport={spy}/>);

  node.setState({
    modalOpen: true,
    availableReports: [{
      id: 'a', name: 'Report A'
      },{
      id: 'b', name: 'Report B'
    }],
    selectedReportId: 'a'
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith('a');
});
