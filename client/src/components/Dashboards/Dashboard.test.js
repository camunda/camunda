import React from 'react';
import {mount} from 'enzyme';

import Dashboard from './Dashboard';
import {loadDashboard, remove, update} from './service';

jest.mock('components', () => {
  const Modal = props => <div>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  const Button = props => (
    <button {...props} active={props.active ? 'true' : undefined}>
      {props.children}
    </button>
  );

  const Dropdown = props => <div>{props.children}</div>;
  Dropdown.Option = props => <Button {...props}>{props.children}</Button>;

  return {
    Modal,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    Button,
    Input: props => (
      <input
        ref={props.reference}
        id={props.id}
        readOnly={props.readOnly}
        type={props.type}
        onChange={props.onChange}
        value={props.value}
        className={props.className}
      />
    ),
    ErrorMessage: props => <div {...props}>{props.text}</div>,
    ControlGroup: props => <div {...props}>{props.children}</div>,
    ShareEntity: () => <div />,
    DashboardView: ({children, reportAddons}) => (
      <div className="DashboardView">
        {children} Addons: {reportAddons}
      </div>
    ),
    Icon: ({type}) => <span>Icon: {type}</span>,
    Dropdown
  };
});

jest.mock('./service', () => {
  return {
    loadDashboard: jest.fn(),
    remove: jest.fn(),
    update: jest.fn()
  };
});

jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>;
    },
    Link: ({children, to, onClick, id}) => {
      return (
        <a id={id} href={to} onClick={onClick}>
          {children}
        </a>
      );
    }
  };
});

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  };
});

jest.mock('react-full-screen', () => ({children}) => <div>{children}</div>);

jest.mock('./AddButton', () => {
  return {AddButton: ({visible}) => <div>AddButton visible: {'' + visible}</div>};
});
jest.mock('./Grid', () => {
  return {Grid: () => <div>Grid</div>};
});
jest.mock('./DimensionSetter', () => {
  return {DimensionSetter: () => <div>DimensionSetter</div>};
});
jest.mock('./DeleteButton', () => {
  return {DeleteButton: () => <button>DeleteButton</button>};
});
jest.mock('./DragBehavior', () => {
  return {DragBehavior: () => <div>DragBehavior</div>};
});
jest.mock('./ResizeHandle', () => {
  return {ResizeHandle: () => <div>ResizeHandle</div>};
});
jest.mock('./AutoRefresh', () => {
  return {
    AutoRefreshBehavior: () => <div>AutoRefreshBehavior</div>,
    AutoRefreshIcon: () => <div>AutoRefreshIcon</div>
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const sampleDashboard = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: [
    {
      id: 1,
      name: 'r1',
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

  node
    .find('.Dashboard__delete-dashboard-modal-button')
    .first()
    .simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the dashboard list on dashboard deletion', async () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  await node
    .find('.Dashboard__delete-dashboard-modal-button')
    .first()
    .simulate('click');

  expect(node).toIncludeText('REDIRECT to /dashboards');
});

it('should render a sharing popover', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  expect(node.find('.Dashboard__share-button').first()).toIncludeText('Share');
});

it('should enter fullscreen mode', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(true);
});

it('should leave fullscreen mode', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true, fullScreenActive: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(false);
});

it('should activate auto refresh mode and set it to numeric value', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  node
    .find('.Dashboard__autoRefreshOption')
    .last()
    .simulate('click');

  expect(typeof node.state('autoRefreshInterval')).toBe('number');
});

it('should deactivate autorefresh mode', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true, autoRefreshInterval: 1000});

  node
    .find('.Dashboard__autoRefreshOption')
    .first()
    .simulate('click');

  expect(node.state('autoRefreshInterval')).toBe(null);
});

it('should add an autorefresh addon when autorefresh mode is active', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true, autoRefreshInterval: 1000});

  expect(node).toIncludeText('Addons: AutoRefreshBehavior');
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

  it('should disable the save button and highlight the input if name empty', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({
      loaded: true,
      name: ''
    });

    expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
    expect(node.find('.Dashboard__save-button')).toBeDisabled();
  });

  it('should select the name input field if dashboard is just created', () => {
    props.match.params.viewMode = 'edit';
    props.location.search = '?new';

    const node = mount(<Dashboard {...props} />);

    node.setState({
      loaded: true,
      ...sampleDashboard
    });

    expect(
      node
        .find('.Dashboard__name-input')
        .at(0)
        .getDOMNode()
    ).toBe(document.activeElement);
  });
});
