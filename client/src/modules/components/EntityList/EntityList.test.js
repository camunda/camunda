import React from 'react';
import {mount, shallow} from 'enzyme';

import EntityList from './EntityList';

import {create, load} from './service';

const sampleEntity = {
  id: '1',
  name: 'Test Entity',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

jest.mock('./service', () => {
  return {
    load: jest.fn(),
    remove: jest.fn(),
    create: jest.fn()
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

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Icon: props => <span>{props.type}</span>,
    Message: props => <p>{props.children}</p>,
    Button: props => <button {...props}>{props.children}</button>
  };
});

load.mockReturnValue([sampleEntity]);

it('should display a loading indicator', () => {
  const node = mount(<EntityList api="endpoint" label="Dashboard" />);

  expect(node).toIncludeText('loading');
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
  expect(node.find('.EntityList__no-entities')).not.toBePresent();
  expect(node.find('ul')).toBePresent();
});

it('should display no-entities indicator if no entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: []
  });

  expect(node.find('.EntityList__no-entities')).toBePresent();
});

it('should display create entity link if no entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: []
  });
  expect(node.find('.EntityList__createLink')).toBePresent();
});

it('should not display create entity link if there are entities', () => {
  const node = mount(shallow(<EntityList api="endpoint" label="Dashboard" />).get(0));

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });
  expect(node.find('.EntityList__createLink')).not.toBePresent();
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

  expect(node.find('.EntityList__createButton')).not.toBePresent();
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

  expect(node.find('.EntityList__createButton')).toBePresent();
  expect(node.find('.EntityList__deleteIcon')).toBePresent();
  expect(node.find('.EntityList__editLink')).toBePresent();
});

it('should not display any operations if none are specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={[]} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__createButton')).not.toBePresent();
  expect(node).not.toIncludeText('EntityList__deleteIcon');
  expect(node).not.toIncludeText('EntityList__editLink');
});

it('should display a create button if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['create']} />).get(0)
  );

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__createButton')).toBePresent();
});

it('should display an edit link if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['edit']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__editLink')).toBePresent();
});

it('should display a delete button if specified', () => {
  const node = mount(
    shallow(<EntityList api="endpoint" label="Dashboard" operations={['delete']} />).get(0)
  );
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__deleteIcon')).toBePresent();
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

  node.find('.EntityList__deleteIcon').simulate('click');

  expect(node.find('.EntityList__delete-modal')).toBePresent();
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
