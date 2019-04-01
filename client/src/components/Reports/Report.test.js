import React from 'react';
import {shallow} from 'enzyme';

import Report from './Report';
import {evaluateReport, loadSingleReport} from './service';

jest.mock('./service', () => {
  return {
    loadSingleReport: jest.fn(),
    evaluateReport: jest.fn()
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const report = {
  id: 'reportID',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: null,
    configuration: {},
    parameters: {},
    visualization: 'table'
  },
  result: [1, 2, 3]
};

loadSingleReport.mockReturnValue(report);
evaluateReport.mockReturnValue(report);

it('should display a loading indicator', () => {
  const node = shallow(<Report {...props} />).dive();

  expect(node.find('LoadingIndicator')).toBePresent();
});

it("should show an error page if report doesn't exist", () => {
  const node = shallow(<Report {...props} />).dive();
  node.setState({
    serverError: 404
  });

  expect(node.find('ErrorPage')).toBePresent();
});

it('should initially load data', () => {
  shallow(<Report {...props} />);

  expect(loadSingleReport).toHaveBeenCalled();
});

it('should initially evaluate the report', () => {
  shallow(<Report {...props} />);

  expect(evaluateReport).toHaveBeenCalled();
});

it('should render ReportEdit component if viewMode is edit', async () => {
  props.match.params.viewMode = 'edit';

  const node = await shallow(<Report {...props} />).dive();
  node.setState({loaded: true, report});

  expect(node.find('ReportEditErrorHandler')).toBePresent();
});

it('should render ReportView component if viewMode is view', async () => {
  props.match.params.viewMode = 'view';

  const node = await shallow(<Report {...props} />).dive();
  node.setState({loaded: true, report});

  expect(node.find('ReportView')).toBePresent();
});

it('should open editCollectionModal when calling openEditCollectionModal', async () => {
  const node = await shallow(<Report {...props} />).dive();
  node.setState({loaded: true, report});

  node.instance().openEditCollectionModal();

  expect(node.find('EditCollectionModal')).toBePresent();
});

it('should invoke loadCollections on mount', async () => {
  const node = await shallow(<Report {...props} />).dive();
  node.instance().loadCollections = jest.fn();
  await node.instance().componentDidMount();

  expect(node.instance().loadCollections).toHaveBeenCalled();
});
