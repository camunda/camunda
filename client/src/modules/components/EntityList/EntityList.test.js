import React from 'react';
import {mount, shallow} from 'enzyme';

import EntityList from './EntityList';

import {create, load, duplicate, update, remove} from './service';

import {checkDeleteConflict} from 'services';

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

jest.mock('services', () => {
  return {
    checkDeleteConflict: jest.fn()
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

jest.mock('./EntityItem', () => props => (
  <li className="entityItem">{JSON.stringify(props.data)}</li>
));

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

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Message: props => <p>{props.children}</p>,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => <input {...props} type="text" />,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    ),
    ConfirmationModal: props => <div className="confirmationModal" />
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
  mount(<EntityList api="endpoint" label="Dashboard" loadOnly="5" />);

  expect(load).toHaveBeenCalledWith('endpoint', '5', undefined);
});

it('should only display the specified amound of result', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" displayOnly="5" />).get(0)
  );

  node.setState({
    loaded: true,
    data: Array(10).fill(sampleEntity)
  });

  await node.update();
  expect(node.find('ul').children().length).toBe(5);
});

it('should display a list with the results', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

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

it('should open confirm modal on delete button click', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  await node.instance().showDeleteModal({
    id: '1',
    name: 'Test Entity'
  });
  await node.update();

  expect(node.find('ConfirmationModal').props().open).toBe(true);
});

it('should set conflict state on delete button click when conflict exists', async () => {
  checkDeleteConflict.mockReturnValue({conflictedItems: [{id: '1', name: 'alert', type: 'Alert'}]});
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  await node.instance().showDeleteModal({
    id: '1',
    name: 'Test Entity'
  });
  await node.update();

  expect(node.find('ConfirmationModal').props().conflict).toEqual({
    items: [{id: '1', name: 'alert', type: 'Alert'}],
    type: 'Delete'
  });
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
  await node.instance().duplicateEntity('1');
  await node.update();
  expect(duplicate).toHaveBeenCalled();
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

it('should increase the elements in the list by 1 when invoking the duplicate onClick', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['duplicate']} />).get(0)
  );

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  load.mockReturnValue([sampleEntity, duplicateEntity]);
  await node.instance().duplicateEntity('1');
  await node.update();

  expect(node.find('ul').children().length).toBe(2);
  expect(node.find('ul')).toIncludeText('copy of');
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

  node.instance().updateEntity(alertEntity);
  await node.update();

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

  node.find('.item Button.createLink').simulate('click');
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

  node.instance().updateEntity(alertEntity);
  await node.update();

  node.instance().confirmContentPanel();

  expect(update).toHaveBeenCalled();
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
  const spy = jest.spyOn(node.instance(), 'createEntity');

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(spy).toHaveBeenCalledWith('combined');
  node.find('button.combineButton').simulate('click');
  await node.update();

  expect(create).toHaveBeenCalled();
});

it('should invok createEntity with parameter "single" when create new Entity is clicked', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['create']} />).get(0)
  );

  const spy = jest.spyOn(node.instance(), 'createEntity');

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(spy).toBeCalledWith('single');

  node.find('button.createButton').simulate('click');
  await node.update();

  expect(create).toHaveBeenCalled();
});

it('should return the visualization type of the given report id', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['create']} />).get(0)
  );

  node.setState({
    loaded: true,
    data: [{...sampleEntity, data: {visualization: 'bar'}}]
  });

  expect(node.instance().getReportVis('1')).toBe('bar');
});

it('should delete a report and its references if it was included in a combined report', async () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Report" operations={['delete']} />).get(0)
  );

  const singleReport = {...sampleEntity, reportType: 'single', data: {visualization: 'bar'}};
  const combinedReport = {
    ...sampleEntity,
    id: '2',
    reportType: 'combined',
    data: {reportIds: ['1', '2']}
  };

  node.setState({
    loaded: true,
    data: [singleReport, combinedReport],
    deleteModalEntity: {
      id: '1'
    }
  });

  node.instance().deleteEntity();

  expect(remove).toHaveBeenCalledWith('1', 'endpoint');
  expect(node.state().data).toHaveLength(1);
  expect(node.state().data[0].data.reportIds).toEqual(['2']);
});
