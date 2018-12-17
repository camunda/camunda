import React from 'react';
import {mount, shallow} from 'enzyme';

import Report from './Report';
import {
  getReportData,
  loadSingleReport,
  remove,
  saveReport,
  loadProcessDefinitionXml
} from './service';

import {checkDeleteConflict, incompatibleFilters} from 'services';

console.error = jest.fn();

jest.mock('components', () => {
  return {
    Button: props => (
      <button {...props} active={props.active ? 'true' : undefined}>
        {props.children}
      </button>
    ),
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
    ReportView: () => <div>ReportView</div>,
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    Icon: ({type}) => <span>Icon: {type}</span>,
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    ErrorPage: () => <div>{`error page`}</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    ),
    Message: ({type, children}) => <div className={'Message Message--' + type}>{children}</div>,
    ConfirmationModal: ({onConfirm, open, onClose, entityName, ...props}) => (
      <div className="ConfirmationModal" {...props}>
        {props.children}
      </div>
    )
  };
});

jest.mock('./service', () => {
  return {
    loadSingleReport: jest.fn(),
    remove: jest.fn(),
    getReportData: jest.fn(),
    saveReport: jest.fn(),
    loadProcessDefinitionXml: jest.fn(),
    isSharingEnabled: jest.fn().mockReturnValue(true)
  };
});

jest.mock('./ColumnRearrangement', () => props => <div>ColumnRearrangement: {props.children}</div>);

jest.mock('services', () => {
  return {
    loadProcessDefinitions: () => [{key: 'key', versions: [{version: 2}, {version: 1}]}],
    checkDeleteConflict: jest.fn(),
    incompatibleFilters: jest.fn()
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

jest.mock('./ReportControlPanel', () => {
  return () => <div>ControlPanel</div>;
});

jest.mock('./CombinedReportPanel', () => {
  return () => <div>CombinedReportPanel</div>;
});

jest.mock('./DecisionControlPanel', () => {
  return () => <div>DecisionControlPanel</div>;
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const sampleReport = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: null,
    configuration: {}
  }
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
  const node = mount(shallow(<Report {...props} />).get(0));

  expect(node.find('.sk-circle')).toBePresent();
});

it("should show an error page if report doesn't exist", async () => {
  const node = await mount(shallow(<Report {...props} />).get(0));
  node.setState({
    serverError: 404
  });

  expect(node).toIncludeText('error page');
});

it('should initially load data', () => {
  mount(shallow(<Report {...props} />).get(0));

  expect(loadSingleReport).toHaveBeenCalled();
});

it('should initially evaluate the report', () => {
  mount(shallow(<Report {...props} />).get(0));

  expect(getReportData).toHaveBeenCalled();
});

it('should display the key properties of a report', () => {
  const node = mount(shallow(<Report {...props} />).get(0));

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
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__edit-button')).toBePresent();
});

it('should open a deletion modal on delete button click', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport
  });

  await node
    .find('.Report__delete-button')
    .first()
    .simulate('click');

  expect(node).toHaveState('confirmModalVisible', true);
});

it('should remove a report when delete is invoked', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport,
    ConfirmModalVisible: true
  });

  node.instance().deleteReport();

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport,
    ConfirmModalVisible: true
  });

  node.instance().deleteReport();
  await node.update();

  expect(node).toIncludeText('REDIRECT to /reports');
});

it('should contain a ReportView with the report evaluation result', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).toIncludeText('ReportView');
});

it('should not contain a Control Panel in edit mode for a combined report', () => {
  props.match.params.viewMode = 'edit';

  const combinedReport = {
    ...sampleReport,
    combined: true,
    result: {
      test: {
        data: {
          visualization: 'test'
        }
      }
    }
  };

  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult: combinedReport, ...combinedReport});

  expect(node).not.toIncludeText('ControlPanel');
});

it('should contain a Control Panel in edit mode for a single report', () => {
  props.match.params.viewMode = 'edit';

  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).toIncludeText('ControlPanel');
});

it('should contain a decision control panel in edit mode for dmn reports', () => {
  props.match.params.viewMode = 'edit';

  const report = {
    ...sampleReport,
    reportType: 'decision'
  };

  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...report});

  expect(node).toIncludeText('DecisionControlPanel');
});

it('should not contain a Control Panel in non-edit mode', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node).not.toIncludeText('ControlPanel');
});

it('should update the report', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));

  await node.instance().componentDidMount();
  await node.instance().updateReport({visualization: 'customTestVis'});

  expect(node.state().data.visualization).toBe('customTestVis');
});

it('should evaluate the report after updating', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));

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

it('should not evaluate the report after updating only the configuration', async () => {
  const node = await mount(shallow(<Report {...props} />).get(0));

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
  await node.instance().updateReport({configuration: 'someOtherConfiguration'});

  expect(getReportData).not.toHaveBeenCalled();
});

it('should reset the report data to its original state after canceling', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));

  await node.instance().componentDidMount();

  const dataBefore = node.state().data;

  await node.instance().updateReport({visualization: 'customTestVis'});
  await node.instance().cancel();

  expect(node.state().data).toEqual(dataBefore);
});

it('should save a changed report', async () => {
  const node = mount(shallow(<Report {...props} />).get(0));

  node.instance().save();

  expect(saveReport).toHaveBeenCalled();
});

it('should render a sharing popover', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__share-button').first()).toIncludeText('Share');
});

it('should show a download csv button with the correct link when report is a table', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport});

  expect(node.find('.Report__csv-download-button')).toBePresent();

  const href = node
    .find('.Report__csv-download-button')
    .getDOMNode()
    .getAttribute('href');

  expect(href).toContain(props.match.params.id);
  expect(href).toContain(sampleReport.name);
});

it('should reflect excluded columns in the csv download link', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({
    loaded: true,
    reportResult,
    ...sampleReport,
    data: {configuration: {excludedColumns: ['prop1', 'var__VariableName']}}
  });

  expect(node.find('.Report__csv-download-button')).toBePresent();

  const href = node
    .find('.Report__csv-download-button')
    .getDOMNode()
    .getAttribute('href');

  expect(href).toContain('?excludedColumns=prop1,variable:VariableName');
});

it('should not show a csv download button when report is not a table', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
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

it('should not show a csv download button when report is for decision', () => {
  const node = mount(shallow(<Report {...props} />).get(0));
  node.setState({loaded: true, reportResult, ...sampleReport, reportType: 'decision'});

  expect(node.find('.Report__csv-download-button')).not.toBePresent();
});

describe('edit mode', async () => {
  it('should add isInvalid prop to the name input is name is empty', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(shallow(<Report {...props} />).get(0));
    await node.instance().componentDidMount();

    await node.setState({
      name: '',
      loaded: true
    });

    expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
  });

  it('should store xml if process definition is changed', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(shallow(<Report {...props} />).get(0));

    await node.instance().componentDidMount();
    await node
      .instance()
      .updateReport({processDefinitionKey: 'asd', processDefinitionVersion: '123'});

    expect(node.state().data.configuration.xml).toBe('some xml');
  });

  it('should reset groupby variables and visualization if process definition is changed', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(shallow(<Report {...props} />).get(0));

    await node.instance().componentDidMount();

    node.setState({
      data: {
        filter: [],
        view: {
          operation: 'foo'
        },
        groupBy: {
          type: 'variable'
        },
        visualization: 'number'
      }
    });

    await node
      .instance()
      .updateReport({processDefinitionKey: 'asd', processDefinitionVersion: '123'});

    expect(node.state().data.groupBy).toBe(null);
    expect(node.state().data.visualization).toBe(null);
  });

  it('should not reset groupby and visualization if process definition is changed, but not grouped by variables', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(shallow(<Report {...props} />).get(0));

    await node.instance().componentDidMount();

    node.setState({
      data: {
        filter: [],
        view: {
          operation: 'foo'
        },
        groupBy: {
          type: 'none'
        },
        visualization: 'number'
      }
    });

    await node
      .instance()
      .updateReport({processDefinitionKey: 'asd', processDefinitionVersion: '123'});

    expect(node.state().data.groupBy).toEqual({type: 'none'});
    expect(node.state().data.visualization).toBe('number');
  });

  it('should provide a link to view mode', async () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true});

    expect(node.find('.Report__save-button')).toBePresent();
    expect(node.find('.Report__cancel-button')).toBePresent();
    expect(node.find('.Report__edit-button')).not.toBePresent();
  });

  it('should provide name edit input', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    expect(node.find('input#name')).toBePresent();
  });

  it('should invoke update on save click', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    node.find('.Report__save-button').simulate('click');

    expect(saveReport).toHaveBeenCalled();
  });

  it('should disable save button if report name is empty', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true, name: ''});

    expect(node.find('.Report__save-button')).toBeDisabled();
  });

  it('should update name on input change', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true, name: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});
    expect(node).toHaveState('name', input);
  });

  it('should reset name on cancel', async () => {
    props.match.params.viewMode = 'edit';
    const node = await mount(shallow(<Report {...props} />).get(0));
    node.setState({loaded: true});

    const input = 'asdf';
    await node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    await node.instance().componentDidMount();

    await node.instance().cancel();
    expect(node).toHaveState('name', 'name');
  });

  it('should invoke cancel', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));

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
    const node = mount(shallow(<Report {...props} />).get(0));

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

  // re-enable this test once https://github.com/airbnb/enzyme/issues/1604 is fixed
  // it('should select the name input field if Report is just created', () => {
  //   props.match.params.viewMode = 'edit';
  //   props.location.search = '?new';

  //   const node = mount(shallow(<Report {...props} />).get(0));

  //   node.setState({
  //     loaded: true,
  //     ...sampleReport
  //   });

  //   expect(
  //     node
  //       .find('.Report__name-input')
  //       .at(0)
  //       .getDOMNode()
  //   ).toBe(document.activeElement);
  // });

  it("should select the only procDef and it's latest version by default", async () => {
    props.match.params.viewMode = 'edit';

    const node = mount(shallow(<Report {...props} />).get(0));

    loadSingleReport.mockReturnValueOnce({});

    await node.instance().componentDidMount();

    expect(node.state().data.processDefinitionKey).toBe('key');
    expect(node.state().data.processDefinitionVersion).toBe(2);
  });

  describe('onlyVisualizationChanged', () => {
    it('should return false if visualization data is not loaded before', async () => {
      const node = mount(shallow(<Report {...props} />).get(0));
      await node.instance().componentDidMount();
      node.setState({
        data: {
          view: 'test view',
          groupBy: {type: 'name'},
          configuration: {}
        }
      });
      const updates = {
        visualization: 'barchart',
        view: 'test view',
        groupBy: {type: 'name'}
      };
      expect(!!node.instance().onlyVisualizationChanged(updates)).toBe(false);
    });

    it('should return false if groupBy or view also changed', async () => {
      const node = mount(shallow(<Report {...props} />).get(0));
      await node.instance().componentDidMount();
      node.setState({
        data: {
          visualization: 'table',
          view: 'test view',
          groupBy: {type: 'name'},
          configuration: {}
        }
      });
      const updates = {
        visualization: 'barchart',
        view: 'another view',
        groupBy: {type: 'another name'}
      };
      expect(node.instance().onlyVisualizationChanged(updates)).toBe(false);
    });

    it('should return true if only visualization has changed', async () => {
      const node = mount(shallow(<Report {...props} />).get(0));
      await node.instance().componentDidMount();
      node.setState({
        data: {
          visualization: 'table',
          view: 'test view',
          groupBy: {type: 'name'},
          configuration: {}
        }
      });
      const updates = {
        visualization: 'barchart',
        view: 'test view',
        groupBy: {type: 'name'}
      };
      expect(node.instance().onlyVisualizationChanged(updates)).toBe(true);
    });
  });

  it('should show a warning message when process instance count exceeds the maximum shown', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    await node.instance().componentDidMount();

    node.setState({
      reportResult: {
        data: {
          visualization: 'table',
          view: {
            operation: 'rawData'
          }
        },
        processInstanceCount: 1500,
        result: new Array(1000)
      }
    });
    expect(node.find('.Message')).toBePresent();
  });

  it('should show a warning message when there are incompatible filter ', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(shallow(<Report {...props} />).get(0));
    await node.instance().componentDidMount();

    incompatibleFilters.mockReturnValue(true);

    node.setState({
      data: {
        visualization: 'table',
        view: {
          operation: 'rawData'
        },
        filter: ['some data']
      }
    });

    expect(node.find('.Message')).toBePresent();
  });

  it('should set conflict state when conflict happens on delete button click', async () => {
    const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];
    checkDeleteConflict.mockReturnValue({
      conflictedItems
    });
    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({
      loaded: true,
      reportResult,
      ...sampleReport
    });

    await node
      .find('.Report__delete-button')
      .first()
      .simulate('click');
    expect(node.state().conflict.type).toEqual('Delete');
    expect(node.state().conflict.items).toEqual(conflictedItems);
  });

  it('should set conflict state when conflict happens on save button click', async () => {
    props.match.params.viewMode = 'edit';
    const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];
    saveReport.mockImplementation(async () => {
      const error = {statusText: 'Conflict', json: async () => ({conflictedItems})};
      throw error;
    });

    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({
      loaded: true,
      reportResult,
      ...sampleReport
    });

    await node.find('.Report__save-button').simulate('click');
    await node.update();

    expect(node.state().conflict.type).toEqual('Save');
    expect(node.state().conflict.items).toEqual(conflictedItems);
  });

  it('should reset the target value when reports get deselected', async () => {
    props.match.params.viewMode = 'edit';

    const combinedReport = {
      ...sampleReport,
      combined: true,
      data: {
        reportIds: ['test'],
        configuration: {
          targetValue: {
            active: true,
            value: 25
          }
        }
      }
    };

    const node = mount(shallow(<Report {...props} />).get(0));
    node.setState({
      loaded: true,
      reportResult: combinedReport,
      ...combinedReport
    });

    getReportData.mockReturnValue(combinedReport);

    node.instance().updateReport({reportIds: []});

    expect(node.state().data.configuration).toEqual({targetValue: null});
  });

  it('should set the correct parameters when updating sorting', () => {
    props.match.params.viewMode = 'edit';

    const ReportComponent = Report.WrappedComponent;

    const node = shallow(<ReportComponent {...props} />);
    node.setState({
      loaded: true,
      ...sampleReport
    });

    node
      .find('ReportView')
      .prop('customProps')
      .table.updateSorting('columnId', 'desc');

    expect(node.state().data.parameters.sorting).toEqual({by: 'columnId', order: 'desc'});
  });
});
