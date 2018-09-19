import React from 'react';
import {mount, shallow} from 'enzyme';

import EntityItem from './EntityItem';

const sampleEntity = {
  id: '1',
  name: 'Test Entity',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

jest.mock('./entityIcons', () => {
  return {
    endpoint: {
      header: props => <svg {...props} />,
      generic: props => <svg {...props} />,
      heat: props => <svg {...props} />,
      combinedline: props => <svg {...props} />
    }
  };
});

jest.mock('components', () => {
  return {
    Icon: props => <span {...props}>{props.type}</span>
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      getHighlightedText: text => text
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

it('should display a list item with the supplied entity information', () => {
  const node = mount(
    <EntityItem api="endpoint" label="Report" operations={[]} data={sampleEntity} />
  );

  expect(node).toIncludeText(sampleEntity.name);
  expect(node).toIncludeText(sampleEntity.lastModifier);
});

it('should render cells content correctly', () => {
  const node = mount(
    <EntityItem api="endpoint" label="Report" operations={['search']} data={sampleEntity} />
  );

  const data = node
    .instance()
    .renderCells([{content: 'test', link: 'test link', parentClassName: 'parent'}]);

  expect(data).toHaveLength(1);
  expect(data[0].type).toBe('span');
  expect(data[0].props.children.props.to).toBe('test link');
});

it('should return a react Link when ContentPanel is not defined', async () => {
  const node = mount(
    <EntityItem api="endpoint" label="Report" operations={['search']} data={sampleEntity} />
  );

  const Link = node.instance().renderLink({
    link: 'testLink',
    content: 'testContent'
  });

  const linkNode = shallow(Link);

  expect(linkNode.props().href).toBe('testLink');
  expect(linkNode).toIncludeText('testContent');
});

it('should display all operations per default', async () => {
  const node = mount(
    <EntityItem api="endpoint" label="Report" showDeleteModal={() => {}} data={sampleEntity} />
  );

  expect(node.find('.deleteIcon')).toBePresent();
  expect(node.find('.editLink')).toBePresent();
});

it('should display an edit link if specified', () => {
  const node = mount(
    <EntityItem api="endpoint" label="Report" operations={['edit']} data={sampleEntity} />
  );

  expect(node.find('.editLink')).toBePresent();
});

it('should display a delete button if specified', () => {
  const node = mount(
    <EntityItem
      api="endpoint"
      label="Report"
      showDeleteModal={() => {}}
      operations={['delete']}
      data={sampleEntity}
    />
  );

  expect(node.find('.deleteIcon')).toBePresent();
});

it('should show a share icon only if entity is shared', () => {
  const node = mount(
    <EntityItem
      api="endpoint"
      label="Report"
      showDeleteModal={() => {}}
      operations={['delete', 'edit']}
      data={{...sampleEntity, shared: true}}
    />
  );

  expect(node).toIncludeText('share');

  node.setProps({
    data: {...sampleEntity, shared: false}
  });

  expect(node).not.toIncludeText('share');
});

it('should display a duplicate icon button if specified', () => {
  const node = mount(
    <EntityItem
      api="endpoint"
      label="Report"
      duplicateEntity={() => {}}
      operations={['duplicate']}
      data={sampleEntity}
    />
  );

  expect(node.find('.duplicateIcon')).toBePresent();
});

it('should invok duplicateEntity when duplicate icon is clicked', async () => {
  const spy = jest.fn();
  const node = mount(
    <EntityItem
      api="endpoint"
      label="Report"
      duplicateEntity={spy}
      operations={['duplicate']}
      data={sampleEntity}
    />
  );

  node.find('Icon.duplicateIcon').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalledWith(sampleEntity.id);
});

it('should invok updateEntity when clicking on an item and contentPanel is defined', async () => {
  const spy = jest.fn();
  const node = mount(
    <EntityItem
      api="endpoint"
      label="Report"
      updateEntity={spy}
      duplicateEntity={() => {}}
      operations={[]}
      data={sampleEntity}
      ContentPanel={{}}
    />
  );

  node.find('.info').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should invok showDeleteModal when a delete button is clicked', async () => {
  const spy = jest.fn();
  const node = mount(
    <EntityItem api="endpoint" label="Report" showDeleteModal={spy} data={sampleEntity} />
  );
  node.find('Icon.deleteIcon').simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalledWith({id: sampleEntity.id, name: sampleEntity.name});
});

it('should return false if the entity is not combined or is combined but does not have any reportIds', () => {
  const node = mount(
    <EntityItem api="endpoint" showDeleteModal={() => {}} label="Report" data={sampleEntity} />
  );

  const isNotEmptyCombined = node
    .instance()
    .isNotEmptyCombined({reportType: 'combined', data: {reportIds: null}});

  expect(!!isNotEmptyCombined).toBe(false);
});

it('should add label combined if the report is combined', () => {
  const node = mount(
    <EntityItem
      api="endpoint"
      showDeleteModal={() => {}}
      label="Report"
      data={{...sampleEntity, reportType: 'combined', data: {reportIds: null}}}
    />
  );
  expect(node).toIncludeText('Combined');
});
