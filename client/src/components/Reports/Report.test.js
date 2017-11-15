import React from 'react';
import {mount} from 'enzyme';

import Report from './Report';
import {getReportData, loadSingleReport, remove, saveReport} from './service';

jest.mock('./service', () => {
  return {
    loadSingleReport: jest.fn(),
    remove: jest.fn(),
    getReportData: jest.fn(),
    saveReport: jest.fn()
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

jest.mock('./ControlPanel', () => {
  return () => <div>ControlPanel</div>
});
jest.mock('./ReportView', () => {
  return () => <div>ReportView</div>
});


const props = {
  match: {params: {id: '1'}}
};

const sampleReport = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
};

loadSingleReport.mockReturnValue(sampleReport);
getReportData.mockReturnValue('some report data');

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it('should display a loading indicator', () => {
  const node = mount(<Report {...props} />);

  expect(node.find('.report-loading-indicator')).toBePresent();
});

it('should initially load data', () => {
  mount(<Report {...props} />);

  expect(loadSingleReport).toHaveBeenCalled();
});

it('should display the key properties of a report', () => {
  const node = mount(<Report {...props} />);

  node.setState({
    loaded: true,
    ...sampleReport
  });

  expect(node).toIncludeText(sampleReport.name);
  expect(node).toIncludeText(sampleReport.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node.find('#edit')).toBePresent();
});


it('should remove a report when delete button is clicked', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  node.find('button').simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /reports');
});

it('should initially evaluate the report', () => {
  mount(<Report {...props} />);

  expect(getReportData).toHaveBeenCalled();
});

it('should contain a ReportView with the report evaluation result', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('ReportView');
});

it('should contain a Control Panel in edit mode', () => {
  props.match.params.viewMode = 'edit';

  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('ControlPanel');
});

it('should not contain a Control Panel in non-edit mode', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node).not.toIncludeText('ControlPanel');
});

it('should update the report', async () => {
  const node = mount(<Report {...props} />);

  await node.instance().updateReport('visualization', 'customTestVis');

  expect(node.state().data.visualization).toBe('customTestVis');
});

it('should evaluate the report after updating', async () => {
  const node = mount(<Report {...props} />);

  getReportData.mockClear();
  await node.instance().updateReport('visualization', 'customTestVis');

  expect(getReportData).toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = mount(<Report {...props} />);

  await node.instance().loadReport();

  const dataBefore = node.state().data;

  await node.instance().updateReport('visualization', 'customTestVis');
  await node.instance().cancel();

  expect(node.state().data).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = mount(<Report {...props} />);

  node.instance().save();

  expect(saveReport).toHaveBeenCalled();
});

describe('edit mode', async () => {

  it('should provide a link to view mode', async () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Report {...props} />);
    node.setState({loaded: true});

    expect(node.find('#save')).toBePresent();
    expect(node.find('#cancel')).toBePresent();
    expect(node.find('#edit')).not.toBePresent();
  });

  it('should provide name edit input', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);
    node.setState({loaded: true, name: 'test name'});

    expect(node.find('input#name')).toBePresent();
  });

  it('should invoke update on save click', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);
    node.setState({loaded: true, name: 'test name'});

    node.find('a#save').simulate('click');

    expect(saveReport).toHaveBeenCalled();
  });

  it('should update name on input change', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);
    node.setState({loaded: true, name: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});
    expect(node).toHaveState('name', input);
  });

  it('should reset name on cancel', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);

    node.setState({loaded: true});

    const input = 'asdf';
    await node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    await node.instance().cancel();
    expect(node).toHaveState('name', 'name');
  });

  it('should invoke cancel', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);

    const spy = jest.spyOn(node.instance(), 'cancel');
    node.setState({loaded: true});

    const input = 'asdf';
    await node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    await node.find('a#cancel').simulate('click');
    expect(spy).toHaveBeenCalled();
    expect(node).toHaveState('name', 'name');
  });
});
