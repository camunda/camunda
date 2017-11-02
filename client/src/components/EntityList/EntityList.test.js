import React from 'react';
import {mount} from 'enzyme';

import EntityList from './EntityList';

import {load, create} from './service';

const sampleEntity = {
  id: '1',
  name: 'Test Entity',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
};

jest.mock('./service', () => {return {
  load: jest.fn(),
  remove: jest.fn(),
  create: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT to {to}</div>},
}});
jest.mock('moment', () => () => {return {
  format: () => 'some date'
}});
jest.mock('../Table', () => {return {
  Table: ({data}) => <table><tbody><tr><td>{JSON.stringify(data)}</td></tr></tbody></table>
}});

load.mockReturnValue([sampleEntity]);

it('should display a loading indicator', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' />);

  expect(node).toIncludeText('loading');
});

it('should initially load data', () => {
  mount(<EntityList api='endpoint' label='Dashboard' />);

  expect(load).toHaveBeenCalled();
});

it('should only load the specified amount of results', () => {
  mount(<EntityList api='endpoint' label='Dashboard' displayOnly='5' />);

  expect(load).toHaveBeenCalledWith('endpoint', '5');
});

it('should display a table with the results', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' />);

  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node).toIncludeText(sampleEntity.name);
  expect(node).toIncludeText(sampleEntity.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should call new entity on click on the new entity button and redirect to the new entity', async () => {
  create.mockReturnValueOnce('2');
  const node = mount(<EntityList api='endpoint' label='Dashboard' />);

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /endpoint/2/edit');
});

it('should display all operations per default', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' />);
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__createButton')).toBePresent();
  expect(node).toIncludeText('EntityList__deleteButton');
  expect(node).toIncludeText('EntityList__editLink');
});

it('should not display any operations if none are specified', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' operations={[]} />);
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__createButton')).not.toBePresent();
  expect(node).not.toIncludeText('EntityList__deleteButton');
  expect(node).not.toIncludeText('EntityList__editLink');
});

it('should display a create button if specified', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' operations={['create']} />);
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node.find('.EntityList__createButton')).toBePresent();
});

it('should display an edit link if specified', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' operations={['edit']} />);
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node).toIncludeText('EntityList__editLink');
});

it('should display a delete button if specified', () => {
  const node = mount(<EntityList api='endpoint' label='Dashboard' operations={['delete']} />);
  node.setState({
    loaded: true,
    data: [sampleEntity]
  });

  expect(node).toIncludeText('EntityList__deleteButton');
});
