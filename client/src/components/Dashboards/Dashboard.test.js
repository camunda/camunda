import React from 'react';
import {mount} from 'enzyme';

import Dashboard from './Dashboard';
import {loadDashboard, remove, update} from './service';

jest.mock('components', () => {
  const Modal = props => <div>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  return {
    Modal,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => <input {...props}/>,
    ControlGroup: props => <div {...props}>{props.children}</div>,
    CopyToClipboard: () => <div></div>
  };
});

jest.mock('./service', () => {
  return {
    loadDashboard: jest.fn(),
    remove: jest.fn(),
    update: jest.fn()
  }
});

jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>
    },
    Link: ({children, to, onClick, id}) => {
      return <a id={id} href={to} onClick={onClick}>{children}</a>
    }
  }
});

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  }
});

jest.mock('./DashboardView', () => {return {DashboardView: ({children, reportAddons}) => <div className='DashboardView'>{children} Addons: {reportAddons}</div>}});

jest.mock('./AddButton', () => {return {AddButton: ({visible}) => <div>AddButton visible: {''+visible}</div>}});
jest.mock('./Grid', () => {return {Grid: () => <div>Grid</div>}});
jest.mock('./DeleteButton', () => {return {DeleteButton: () => <button>DeleteButton</button>}});
jest.mock('./DragBehavior', () => {return {DragBehavior: () => <div>DragBehavior</div>}});
jest.mock('./ResizeHandle', () => {return {ResizeHandle: () => <div>ResizeHandle</div>}});

const props = {
  match: {params: {id: '1'}}
};

const sampleDashboard = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: [
    {
      id: 1,
      name : 'r1',
      position: {x: 0, y: 0},
      dimensions: {width: 1, height: 1}
    },
    {
      id: 2,
      name: 'r2',
      position: {x: 0, y: 2},
      dimensions: {width: 1, height: 1}
    }
  ]
};

loadDashboard.mockReturnValue(sampleDashboard);

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it('should display a loading indicator', () => {
  const node = mount(<Dashboard {...props} />);

  expect(node.find('.dashboard-loading-indicator')).toBePresent();
});

it('should initially load data', () => {
  mount(<Dashboard {...props} />);

  expect(loadDashboard).toHaveBeenCalled();
});

it('should display the key properties of a dashboard', () => {
  const node = mount(<Dashboard {...props} />);

  node.setState({
    loaded: true,
    ...sampleDashboard
  });

  expect(node).toIncludeText(sampleDashboard.name);
  expect(node).toIncludeText(sampleDashboard.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  expect(node.find('.Dashboard__edit-button')).toBePresent();
});

it('should remove a dashboard when delete button is clicked', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  node.find('.Dashboard__delete-dashboard-modal-button').first().simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the dashboard list on dashboard deletion', async () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  await node.find('.Dashboard__delete-dashboard-modal-button').first().simulate('click');

  expect(node).toIncludeText('REDIRECT to /dashboards');
});

it('should show a modal on share button click', () => {
  const node = mount(<Dashboard {...props} />);

  node.setState({loaded: true});

  node.find('.Dashboard__share-button').first().simulate('click');

  expect(node.find('.Dashboard__share-modal')).toHaveProp('open', true);
});

it('should hide the modal on close button click', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  node.find('.Dashboard__share-button').first().simulate('click');
  node.find('.Dashboard__close-share-modal-button').first().simulate('click');

  expect(node.find('.Dashboard__share-modal')).toHaveProp('open', false);
});


describe('edit mode', async () => {

  it('should provide a link to view mode', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node.find('.Dashboard__save-button')).toBePresent();
    expect(node.find('.Dashboard__cancel-button')).toBePresent();
    expect(node.find('.Dashboard__edit-button')).not.toBePresent();
  });

  it('should provide name edit input', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    expect(node.find('input#name')).toBePresent();
  });

  it('should invoke update on save click', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    node.find('.Dashboard__save-button').simulate('click');

    expect(update).toHaveBeenCalled();
  });

  it('should update name on input change', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});
    expect(node).toHaveState('name', input);
  });

  it('should reset name on cancel', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name', originalName: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    node.find('.Dashboard__cancel-button').simulate('click');
    expect(node).toHaveState('name', 'test name');
  });

  it('should contain a Grid', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('Grid');
  });

  it('should contain an AddButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('AddButton');
  });

  it('should contain a DeleteButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('DeleteButton');
  });

  it('should hide the AddButton based on the state', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, addButtonVisible: false});

    expect(node).toIncludeText('AddButton visible: false');
  });

  it('should add DragBehavior', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('DragBehavior');
  });

  it('should add a resize handle', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('ResizeHandle');
  });
});
