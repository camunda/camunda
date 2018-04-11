import React from 'react';
import {mount} from 'enzyme';

import Report from './Report';
import {
  getReportData,
  loadSingleReport,
  remove,
  saveReport,
  loadProcessDefinitionXml
} from './service';

import {loadProcessDefinitions} from 'services';

jest.mock('components', () => {
  const Modal = props => <div {...props}>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  return {
    Modal,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    Button: props => <button {...props}>{props.children}</button>,
    ErrorMessage: props => <div {...props}>{props.text}</div>,
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
    ShareEntity: () => <div />,
    ReportView: () => <div>ReportView</div>,
    Icon: ({type}) => <div>{type}</div>
  };
});

jest.mock('./service', () => {
  return {
    loadSingleReport: jest.fn(),
    remove: jest.fn(),
    getReportData: jest.fn(),
    saveReport: jest.fn(),
    loadProcessDefinitionXml: jest.fn()
  };
});

jest.mock('services', () => {
  return {
    loadProcessDefinitions: () => [{key: 'key', versions: [{version: 2}, {version: 1}]}]
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

jest.mock('./ControlPanel', () => {
  return () => <div>ControlPanel</div>;
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const sampleReport = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

const reportResult = {
  data: {
    visualization: 'table'
  },
  result: [1, 2, 3]
};

loadSingleReport.mockReturnValue(sampleReport);
getReportData.mockReturnValue(reportResult);
loadProcessDefinitionXml.mockReturnValue('some xml');

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

it('should initially evaluate the report', () => {
  mount(<Report {...props} />);

  expect(getReportData).toHaveBeenCalled();
});

it('should display the key properties of a report', () => {
  const node = mount(<Report {...props} />);

  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport
  });

  expect(node).toIncludeText(sampleReport.name);
  expect(node).toIncludeText(sampleReport.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__edit-button')).toBePresent();
});

it('should open a deletion modal on delete button click', async () => {
  const node = mount(<Report {...props} />);
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport
  });

  await node
    .find('.Report__delete-button')
    .first()
    .simulate('click');

  expect(node).toHaveState('deleteModalVisible', true);
});

it('should remove a report when delete button is clicked', () => {
  const node = mount(<Report {...props} />);
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport,
    deleteModalVisible: true
  });

  node
    .find('.Report__delete-report-modal-button')
    .first()
    .simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = mount(<Report {...props} />);
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport,
    deleteModalVisible: true
  });

  await node
    .find('.Report__delete-report-modal-button')
    .first()
    .simulate('click');

  expect(node).toIncludeText('REDIRECT to /reports');
});

it('should contain a ReportView with the report evaluation result', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).toIncludeText('ReportView');
});

it('should contain a Control Panel in edit mode', () => {
  props.match.params.viewMode = 'edit';

  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).toIncludeText('ControlPanel');
});

it('should not contain a Control Panel in non-edit mode', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).not.toIncludeText('ControlPanel');
});

it('should update the report', async () => {
  const node = mount(<Report {...props} />);

  await node.instance().componentDidMount();
  await node.instance().updateReport({visualization: 'customTestVis'});

  expect(node.state().data.visualization).toBe('customTestVis');
});

it('should evaluate the report after updating', async () => {
  const node = mount(<Report {...props} />);

  node.setState({
    data: {
      processDefinitionKey: 'test key',
      processDefinitionVersion: 'test version',
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: 'bar'
      },
      visualization: 'number'
    },
    ...sampleReport
  });

  getReportData.mockClear();
  await node.instance().updateReport({visualization: 'customTestVis'});

  expect(getReportData).toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = mount(<Report {...props} />);

  await node.instance().componentDidMount();

  const dataBefore = node.state().data;

  await node.instance().updateReport({visualization: 'customTestVis'});
  await node.instance().cancel();

  expect(node.state().data).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = mount(<Report {...props} />);

  node.instance().save();

  expect(saveReport).toHaveBeenCalled();
});

it('should render a sharing popover', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__share-button').first()).toIncludeText('Share');
});

it('should show a download csv button with the correct link when report is a table', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__csv-download-button')).toBePresent();

  const href = node
    .find('.Report__csv-download-button')
    .getDOMNode()
    .getAttribute('href');

  expect(href).toContain(props.match.params.id);
  expect(href).toContain(sampleReport.name);
});

it('should not show a csv download button when report is not a table', () => {
  const node = mount(<Report {...props} />);
  node.setState({
    loaded: true,
    reportResult: {
      data: {
        visualization: 'someOtherVis'
      },
      result: [1, 2, 3]
    },
    ...sampleReport
  });

  expect(node.find('.Report__csv-download-button')).not.toBePresent();
});

describe('edit mode', async () => {
  it('should remove flow node and variable filter after changing ProcDef', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(<Report {...props} />);

    node.setState(prevState => ({
      loaded: true,
      data: {
        ...node.instance().initializeReport(),
        filter: [
          {
            data: 'foo',
            type: 'bar'
          },
          {
            data: 'foo',
            type: 'executedFlowNodes'
          },
          {
            data: 'foo',
            type: 'variable'
          }
        ],
        view: {
          operation: 'operation'
        },
        groupBy: {
          type: 'type'
        },
        visualization: 'visualization'
      }
    }));

    await node.instance().updateReport({processDefinitionKey: 'foo'});

    expect(node.state().data.filter.length).toBe(1);
    expect(node.state().data.filter[0].data).toBe('foo');
    expect(node.state().data.filter[0].type).toBe('bar');
  });

  it('should add isInvalid prop to the name input is name is empty', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(<Report {...props} />);
    await node.instance().componentDidMount();

    await node.setState({
      name: '',
      loaded: true
    });

    expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
  });

  it('should store xml if process definition is changed', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(<Report {...props} />);

    await node.instance().componentDidMount();
    await node
      .instance()
      .updateReport({processDefinitionKey: 'asd', processDefinitionVersion: '123'});

    expect(node.state().data.configuration.xml).toBe('some xml');
  });

  it('should provide a link to view mode', async () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Report {...props} />);
    node.setState({loaded: true});

    expect(node.find('.Report__save-button')).toBePresent();
    expect(node.find('.Report__cancel-button')).toBePresent();
    expect(node.find('.Report__edit-button')).not.toBePresent();
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

    node.find('.Report__save-button').simulate('click');

    expect(saveReport).toHaveBeenCalled();
  });

  it('should disable save button if report name is empty', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Report {...props} />);
    node.setState({loaded: true, name: ''});

    expect(node.find('.Report__save-button')).toBeDisabled();
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
    const node = await mount(<Report {...props} />);
    node.setState({loaded: true});

    const input = 'asdf';
    await node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    await node.instance().componentDidMount();

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
    await node.instance().componentDidMount();

    await node.find('.Report__cancel-button').simulate('click');
    expect(spy).toHaveBeenCalled();
    expect(node).toHaveState('name', 'name');
  });

  it('should use original data as result data if report cant be evaluated on cancel', async () => {
    const node = mount(<Report {...props} />);

    await node.instance().componentDidMount();

    node.setState({
      loaded: true,
      originalData: {
        processDefinitionKey: '123'
      }
    });
    getReportData.mockReturnValueOnce(null);
    await node.instance().cancel();

    expect(node.state().reportResult.data.processDefinitionKey).toEqual('123');
  });

  it('should select the name input field if Report is just created', () => {
    props.match.params.viewMode = 'edit';
    props.location.search = '?new';

    const node = mount(<Report {...props} />);

    node.setState({
      loaded: true,
      ...sampleReport
    });

    expect(
      node
        .find('.Report__name-input')
        .at(0)
        .getDOMNode()
    ).toBe(document.activeElement);
  });

  it("should select the only procDef and it's latest version by default", async () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Report {...props} />);
    await node.instance().componentDidMount();

    expect(node.state().data.processDefinitionKey).toBe('key');
    expect(node.state().data.processDefinitionVersion).toBe(2);
  });
});
