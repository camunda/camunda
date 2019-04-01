import React from 'react';
import {mount, shallow} from 'enzyme';

import ThemedDashboard from './Dashboard';
import {loadDashboard, remove, isAuthorizedToShareDashboard} from './service';
import {checkDeleteConflict} from 'services';

const {WrappedComponent: Dashboard} = ThemedDashboard;

console.error = jest.fn();

jest.mock('components', () => {
  const Button = props => (
    <button {...props} active={props.active ? 'true' : undefined}>
      {props.children}
    </button>
  );

  const Dropdown = props => <div>{props.children}</div>;
  Dropdown.Option = props => <Button {...props}>{props.children}</Button>;

  return {
    Button,
    Input: props => (
      <input
        id={props.id}
        readOnly={props.readOnly}
        type={props.type}
        onChange={props.onChange}
        onBlur={props.onBlur}
        value={props.value}
        name={props.name}
        className={props.className}
      />
    ),
    ShareEntity: () => <div />,
    DashboardView: ({children, reportAddons}) => (
      <div className="DashboardView">
        {children} Addons: {reportAddons}
      </div>
    ),
    Icon: ({type}) => <span>Icon: {type}</span>,
    Dropdown,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    ErrorPage: () => <div>{`error page`}</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    ),
    ConfirmationModal: ({onConfirm, open, onClose, entityName, ...props}) => (
      <div className="ConfirmationModal" {...props}>
        {props.children}
      </div>
    ),
    EntityNameForm: ({children}) => <div>{children}</div>,
    CollectionsDropdown: () => <div />,
    EditCollectionModal: () => <div />
  };
});

jest.mock('./service', () => {
  return {
    loadDashboard: jest.fn(),
    remove: jest.fn(),
    update: jest.fn(),
    isAuthorizedToShareDashboard: jest.fn(),
    isSharingEnabled: jest.fn().mockReturnValue(true)
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

jest.mock('services', () => ({
  getEntitiesCollections: jest.fn().mockReturnValue({}),
  toggleEntityCollection: fn => fn(),
  checkDeleteConflict: jest.fn(),
  loadEntity: jest.fn().mockReturnValue([])
}));

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
isAuthorizedToShareDashboard.mockReturnValue(true);

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it("should show an error page if dashboard doesn't exist", async () => {
  const node = await mount(shallow(<Dashboard {...props} />).get(0));

  await node.setState({
    serverError: 404
  });

  expect(node).toIncludeText('error page');
});

it('should display a loading indicator', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));

  expect(node.find('.sk-circle')).toBePresent();
});

it('should initially load data', () => {
  mount(<Dashboard {...props} />);

  expect(loadDashboard).toHaveBeenCalled();
});

it('should display the key properties of a dashboard', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));

  node.setState({
    loaded: true,
    ...sampleDashboard
  });

  expect(node).toIncludeText(sampleDashboard.name);
  expect(node).toIncludeText(sampleDashboard.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node.find('.edit-button')).toBePresent();
});

it('should remove a dashboard on dashboard deletion', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  node.instance().deleteDashboard();

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the dashboard list on dashboard deletion', async () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  // the componentDidUpdate is mocked because it resets the redirect state
  // which prevents the redirect component from rendering while testing
  node.instance().componentDidUpdate = jest.fn();

  node.setState({
    loaded: true,
    deleteModalVisible: true
  });

  await node.instance().deleteDashboard();

  expect(node).toIncludeText('REDIRECT to /');
});

it('should render a sharing popover', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node.find('.share-button').first()).toIncludeText('Share');
});

it('should enter fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(true);
});

it('should leave fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});

  node
    .find('.Dashboard__fullscreen-button')
    .first()
    .simulate('click');

  expect(node.state('fullScreenActive')).toBe(false);
});

it('should activate auto refresh mode and set it to numeric value', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  node
    .find('.Dashboard__autoRefreshOption')
    .last()
    .simulate('click');

  expect(typeof node.state('autoRefreshInterval')).toBe('number');
});

it('should deactivate autorefresh mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 1000});

  node
    .find('.Dashboard__autoRefreshOption')
    .first()
    .simulate('click');

  expect(node.state('autoRefreshInterval')).toBe(null);
});

it('should add an autorefresh addon when autorefresh mode is active', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 1000});

  expect(node).toIncludeText('Addons: AutoRefreshBehavior');
});

it('should invoke the renderDashboard function after the interval duration ends', async () => {
  jest.useFakeTimers();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, autoRefreshInterval: 600});

  node.instance().renderDashboard = jest.fn();
  node.update();
  node.instance().setAutorefresh(600)();
  jest.runTimersToTime(700);
  expect(node.instance().renderDashboard).toHaveBeenCalledTimes(1);
});

it('should have a toggle theme button that is only visible in fullscreen mode', () => {
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  expect(node).not.toIncludeText('Toggle Theme');

  node.setState({fullScreenActive: true});

  expect(node).toIncludeText('Toggle Theme');
});

it('should toggle the theme when clicking the toggle theme button', () => {
  const spy = jest.fn();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});
  node.setProps({toggleTheme: spy});

  node
    .find('button')
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when exiting fullscreen mode', () => {
  const spy = jest.fn();
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true, fullScreenActive: true});
  node.setProps({toggleTheme: spy, theme: 'dark'});

  node.instance().toggleFullscreen();

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when the component is unmounted', async () => {
  const spy = jest.fn();
  const node = mount(shallow(<Dashboard {...props} />).get(0));

  await node.instance().componentDidMount();

  node.setState({loaded: true, fullScreenActive: true});
  node.setProps({toggleTheme: spy, theme: 'dark'});

  node.unmount();

  expect(spy).toHaveBeenCalled();
});

it('should set conflict state when conflict happens on delete button click', async () => {
  const conflictedItems = [{id: '1', name: 'collection', type: 'Collection'}];
  checkDeleteConflict.mockReturnValue({
    conflictedItems
  });
  const node = mount(shallow(<Dashboard {...props} />).get(0));
  node.setState({loaded: true});

  await node
    .find('.delete-button')
    .first()
    .prop('onClick')();
  expect(node.state().conflicts).toEqual(conflictedItems);
});

describe('edit mode', async () => {
  it('should reset name on cancel', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, name: 'name', originalName: 'test name'});

    node.instance().cancelChanges();

    expect(node).toHaveState('name', 'test name');
  });

  it('should contain a Grid', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('Grid');
  });

  it('should contain an AddButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('AddButton');
  });

  it('should contain a DeleteButton', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('DeleteButton');
  });

  it('should hide the AddButton based on the state', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true, addButtonVisible: false});

    expect(node).toIncludeText('AddButton visible: false');
  });

  it('should add DragBehavior', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('DragBehavior');
  });

  it('should add a resize handle', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    expect(node).toIncludeText('ResizeHandle');
  });

  it('should disable the share button if not authorized', () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true,
      name: '',
      isAuthorizedToShare: false,
      sharingEnabled: true
    });

    const shareButton = node.find('.share-button');
    expect(shareButton).toBeDisabled();
    expect(shareButton.props()).toHaveProperty(
      'tooltip',
      "You are not authorized to share the dashboard,  because you don't have access to all reports on the dashboard!"
    );
  });

  it('should enable share button if authorized', () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true,
      name: '',
      isAuthorizedToShare: true,
      sharingEnabled: true
    });

    const shareButton = node.find('.share-button');
    expect(shareButton).not.toBeDisabled();
  });

  it('should open editCollectionModal when calling openEditCollectionModal', async () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({loaded: true});

    node.instance().openEditCollectionModal();
    await node.update();

    expect(node.find('EditCollectionModal')).toBePresent();
  });

  it('should invoke loadCollections on mount', async () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.instance().loadCollections = jest.fn();
    await node.instance().componentDidMount();

    expect(node.instance().loadCollections).toHaveBeenCalled();
  });

  it('should render collections dropdown', async () => {
    const node = mount(shallow(<Dashboard {...props} />).get(0));
    node.setState({
      loaded: true
    });

    expect(node.find('CollectionsDropdown')).toBePresent();
  });

  // re-enable this test once https://github.com/airbnb/enzyme/issues/1604 is fixed
  // it('should select the name input field if dashboard is just created', () => {
  //   props.match.params.viewMode = 'edit';
  //   props.location.search = '?new';

  //   const node = mount(shallow(<Dashboard {...props} />).get(0))

  //   node.setState({
  //     loaded: true,
  //     ...sampleDashboard
  //   });

  //   expect(
  //     node
  //       .find('.Dashboard__name-input')
  //       .at(0)
  //       .getDOMNode()
  //   ).toBe(document.activeElement);
  // });
});
