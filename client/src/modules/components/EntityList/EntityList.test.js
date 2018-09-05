import React from 'react';
import {mount, shallow} from 'enzyme';

import EntityList from './EntityList';

import {create, load, duplicate, update} from './service';

const sampleEntity = {
  id: '1',
  name: 'Test Entity',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

const duplicateEntity = {
  id: '2',
  name: 'copy of "Test Entity"',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:12:11.1111+0200'
};

const alertEntity = {id: '1', name: 'preconfigured alert', reportId: '2'};

jest.mock('./service', () => {
  return {
    load: jest.fn(),
    remove: jest.fn(),
    create: jest.fn(),
    duplicate: jest.fn(),
    update: jest.fn()
  };
});

jest.mock('./entityIcons', () => {
  return {
    endpoint: {
      header: props => <svg {...props} />,
      generic: props => <svg {...props} />,
      heat: props => <svg {...props} />
    }
  };
});

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>;
    }
  };
});

jest.mock('moment', () => (...params) => {
  const initialData = params;
  return {
    format: () => 'some date',
    getInitialData: () => {
      return initialData;
    },
    isBefore: date => {
      return new Date(initialData) < new Date(date.getInitialData());
    }
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      getHighlightedText: text => text
    }
  };
});

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Icon: props => <span>{props.type}</span>,
    Message: props => <p>{props.children}</p>,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => <input {...props} type="text" />,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

load.mockReturnValue([sampleEntity]);

const ContentPanel = props => (
  <span>
    ContentPanel: <span id="ModalProps">{JSON.stringify(props)}</span>
  </span>
);

it('should display a loading indicator', () => {
  const node = mount(<EntityList api="endpoint" label="Dashboard" />);

  expect(node.find('.sk-circle')).toBePresent();
});

it('should initially load data', () => {
  mount(<EntityList api="endpoint" label="Dashboard" />);

  expect(load).toHaveBeenCalled();
});

it('should only load the specified amount of results', () => {
  mount(<EntityList api="endpoint" label="Dashboard" displayOnly="5" />);

  expect(load).toHaveBeenCalledWith('endpoint', '5', undefined);
});

it('should display a list with the results', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node).toIncludeText(sampleEntity.name);
  expect(node).toIncludeText(sampleEntity.lastModifier);
  expect(node).toIncludeText('some date');
  expect(node.find('noEntities')).not.toBePresent();
  expect(node.find('ul')).toBePresent();
});

it('should display no-entities indicator if no entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: []
  });

  expect(node.find('.noEntities')).toBePresent();
});

it('should display create entity link if no entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: []
  });
  expect(node.find('.createLink')).toBePresent();
});

it('should not display create entity link if there are entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  expect(node.find('.createLink')).not.toBePresent();
});

it('should not display create entity button on home page', () => {
  const node = mount(
    shallow(
      <EntityList
        includeViewAllLink={true}
        api="endpoint"
        label="Dashboard"
        operations={['edit']}
      />
    ).get(0)
  );

  node.setState({
    loaded: true,
    data: []
  });

  expect(node.find('.createButton')).not.toBePresent();
});

it('should call new entity on click on the new entity button and redirect to the new entity', async () => {
  create.mockReturnValueOnce('2');
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /endpoint/2/edit');
});

it('should display all operations per default', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.createButton')).toBePresent();
  expect(node.find('.deleteIcon')).toBePresent();
  expect(node.find('.editLink')).toBePresent();
});

it('should not display any operations if none are specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={[]} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.createButton')).not.toBePresent();
  expect(node).not.toIncludeText('deleteIcon');
  expect(node).not.toIncludeText('editLink');
});

it('should display a create button if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['create']} />).get(0)
  );

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.createButton')).toBePresent();
});

it('should display an edit link if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['edit']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.editLink')).toBePresent();
});

it('should display a delete button if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.deleteIcon')).toBePresent();
});

it('should be able to sort by date', async () => {
  const node = mount(
    shallow(
      <EntityList
        api="endpoint"
        label="Dashboard"
        operations={['create']}
        sortBy={'lastModified'}
      />
    ).get(0)
  );
  const sampleEntity2 = {
    id: '2',
    name: 'Test Entity 2',
    lastModifier: 'Admin 2',
    lastModified: '2017-11-11T11:12:11.1111+0200'
  };
  load.mockReturnValue([sampleEntity2, sampleEntity]);

  //this will make method to be invoked twice, but we can wait on second call
  await node.instance().componentDidMount();
  expect(load).toBeCalledWith('endpoint', undefined, 'lastModified');
  expect(node.state().data[0]).toEqual(sampleEntity2);
});

it('should open deletion modal on delete button click', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  node.find('.deleteIcon').simulate('click');

  expect(node.find('.deleteModal')).toBePresent();
});

it('should display an error if error occurred', () => {
  const error = {errorMessage: 'There was an error'};
  const node = mount(
    shallow(
      <EntityList api="endpoint" label="Dashboard" error={error} operations={['delete']} />
    ).get(0)
  );

  expect(node).toIncludeText('There was an error');
});

it('should show a share icon only if entity is shared', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete', 'edit']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [{...sampleEntity, shared: true}]
  });

  expect(node).toIncludeText('share');

  node.setState({
    data: [{...sampleEntity, shared: false}]
  });

  expect(node).not.toIncludeText('share');
});

it('should display a duplicate icon button if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['duplicate']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  expect(node.find('.duplicateIcon')).toBePresent();
});

it('should invoke duplicate on click', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['duplicate']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  load.mockReturnValue([sampleEntity, duplicateEntity]);
  await node.instance().duplicateEntity('1')();
  await node.update();
  expect(duplicate).toHaveBeenCalled();
});

it('should increase the elements in the list by 1 when invoking the duplicate onClick', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['duplicate']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  load.mockReturnValue([sampleEntity, duplicateEntity]);
  await node.instance().duplicateEntity('1')();
  await node.update();
  expect(node.find('ul').children().length).toBe(2);
  expect(node.find('ul')).toIncludeText('copy of "Test Entity"');
});

it('should display a search input if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['search']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.input')).toBePresent();
});

it('should when typing a search query Keep only those entries, where the provided value matches anything in the name', () => {
  const entries = ['foooooo', 'barfoobar', 'barfoo', 'bfbaroobar'].map(el => ({name: el}));

  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['search']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: entries,
    query: 'foo'
  });

  expect(node).not.toIncludeText('bfbaroobar');
  expect(node).toIncludeText('barfoo');
  expect(node).toIncludeText('foooooo');
  expect(node).toIncludeText('barfoobar');
});

it('should when typing a search query filter value in case insensitive', () => {
  const entries = ['FOO', 'FoO', 'foo', 'fOO'].map(el => ({name: el}));
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['search']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: entries,
    query: 'foo'
  });

  expect(node).toIncludeText('FOO');
  expect(node).toIncludeText('FoO');
  expect(node).toIncludeText('foo');
  expect(node).toIncludeText('fOO');
});

it('should render cells content correctly', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['search']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  const data = node
    .instance()
    .renderCells([{content: 'test', link: 'test link', parentClassName: 'parent'}]);

  expect(data).toHaveLength(1);
  expect(data[0].type).toBe('span');
  expect(data[0].props.children.props.to).toBe('test link');
});

it('should include an edit/add modal after reports are loaded', async () => {
  load.mockReturnValue([alertEntity]);
  const node = mount(
    shallow(
      <EntityList api="endpoint" label="Alert" operations={['Edit']} ContentPanel={ContentPanel} />
    ).get(0)
  );
  await node.instance().componentDidMount();
  node.setState({
    loaded: true,
    editEntity: {}
  });
  expect(node).toIncludeText('ContentPanel');
});

it('should pass an alert entity configuration to the edit/add modal', async () => {
  load.mockReturnValue([alertEntity]);

  const node = mount(
    shallow(
      <EntityList api="endpoint" label="Alert" operations={['Edit']} ContentPanel={ContentPanel} />
    ).get(0)
  );

  await node.instance().componentDidMount();
  node.setState({
    loaded: true
  });

  node.find('.info').simulate('click');

  expect(node.find('#ModalProps')).toIncludeText('preconfigured alert');
});

it('should invoke openNewContentPanel when click on create new button', async () => {
  load.mockReturnValue([]);

  const node = mount(
    shallow(
      <EntityList api="endpoint" label="Alert" operations={['Edit']} ContentPanel={ContentPanel} />
    ).get(0)
  );
  const spy = jest.spyOn(node.instance(), 'openNewContentPanel');

  await node.instance().componentDidMount();
  node.setState({
    loaded: true
  });

  node.find('a').simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should invok update when entityId is already available', async () => {
  load.mockReturnValue([alertEntity]);

  const node = mount(
    shallow(
      <EntityList api="endpoint" label="Alert" operations={['Edit']} ContentPanel={ContentPanel} />
    ).get(0)
  );

  await node.instance().componentDidMount();
  node.setState({
    loaded: true
  });

  node.find('.info').simulate('click');

  node.instance().confirmContentPanel();

  expect(update).toHaveBeenCalled();
});

it('should return a react Link when ContentPanel is not defined', async () => {
  load.mockReturnValue([alertEntity]);

  const node = mount(
    shallow(<EntityList api="endpoint" label="Alert" operations={['Edit']} />).get(0)
  );

  await node.instance().componentDidMount();
  node.setState({
    loaded: true
  });

  const Link = node.instance().renderLink({
    link: 'testLink',
    content: 'testContent'
  });
  const linkNode = shallow(Link);

  expect(linkNode.props().href).toBe('testLink');
  expect(linkNode).toIncludeText('testContent');
});

describe('getEntityIconName', () => {
  let node = {};
  beforeEach(
    async () =>
      (node = await mount(
        shallow(<EntityList api="endpoint" label="report" operations={['Edit']} />).get(0)
      ))
  );

  it('should return the endoint name if data is null', () => {
    const name = node.instance().getEntityIconName({data: null});
    expect(name).toBe('generic');
  });

  it('should return the endoint name if visualization is empty', () => {
    const name = node.instance().getEntityIconName({data: {visualization: ''}});
    expect(name).toBe('generic');
  });

  it('should return the endpoint name along with the visualization name if visualization is defined', () => {
    const name = node.instance().getEntityIconName({data: {visualization: 'heat'}});
    expect(name).toBe('heat');
  });
});

it('should display a button to create combined report if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['combine']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.combineButton')).toBePresent();
});

it('should invok createEntity with parameter "combined" when create combined button is clicked', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['combine']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  const spy = jest.spyOn(node.instance(), 'createEntity');
  node.find('button.combineButton').simulate('click');

  await node.update();

  expect(spy).toBeCalledWith('combined');
});

it('should invok createEntity with parameter "single" when create new Entity is clicked', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['create']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  const spy = jest.spyOn(node.instance(), 'createEntity');
  node.find('button.createButton').simulate('click');

  await node.update();

  expect(spy).toBeCalledWith('single');
});
