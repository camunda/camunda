import React from 'react';
import {mount} from 'enzyme';

import AddButton from './AddButton';
import DashboardObject from './DashboardObject';
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
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>
  };
});

jest.mock('./service', () => {return {
  loadReports: jest.fn().mockReturnValue([])
}});

jest.mock('./DashboardObject', () => ({children}) => <div className="DashboardObject">{children}</div>);

const props = {
  tileDimensions: {
    columns: 18
  },
  reports: []
};


it('should load the available reports', () => {
  mount(<AddButton {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should open a modal on click', () => {
  const node = mount(<AddButton {...props} />);

  node.find('.AddButton').simulate('click');

  expect(node.find('#modal_header')).toIncludeText('Add a Report');
});

it('should render a select element with the available reports as options', () => {
  const node = mount(<AddButton {...props} />);

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
  const node = mount(<AddButton {...props} addReport={spy}/>);

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

  expect(spy).toHaveBeenCalledWith({
    dimensions: {
      height: 3,
      width: 3
    },
    position: {
      x: 0,
      y: 0
    },
    id: 'a'
  });
});

it('should place the addButton where is no Report', () => {
  const node = mount(<AddButton {...props} reports={[
    {
      position: {x: 0, y: 0},
      dimensions: {width: 3, height: 1},
      id: '1'
    },
    {
      position: {x: 2, y: 0},
      dimensions: {width: 1, height: 4},
      id: '2'
    },
    {
      position: {x: 3, y: 1},
      dimensions: {width: 2, height: 2},
      id: '3'
    }
  ]} />);

  const addButtonPosition = node.instance().getAddButtonPosition();

  expect(addButtonPosition.x).toBe(5);
  expect(addButtonPosition.y).toBe(0);
})
