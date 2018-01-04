import React from 'react';
import {mount} from 'enzyme';

import {loadProcessDefinitionXml, loadFrequencyData} from './service';

import Analysis from './Analysis';

jest.mock('./ControlPanel', () => () => <div>ControlPanel</div>);
jest.mock('components', () => {return {
  BPMNDiagram: () => <div>BPMNDiagram</div>
}});
jest.mock('./service', () => {return {
  loadProcessDefinitionXml: jest.fn(),
  loadFrequencyData: jest.fn()
}});
jest.mock('./DiagramBehavior', () => () => <div>DiagramBehavior</div>);
jest.mock('./Statistics', () => () => <div>Statistics</div>);

it('should contain a control panel', () => {
  const node = mount(<Analysis />);

  expect(node).toIncludeText('ControlPanel');
});

it('should load the process definition xml when the process definition id is updated', () => {
  const node = mount(<Analysis />);

  loadProcessDefinitionXml.mockClear();
  node.instance().updateConfig('processDefinitionId', 'someId');

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('someId');
});

it('should load frequency data when the process definition id changes', () => {
  const node = mount(<Analysis />);

  loadFrequencyData.mockClear();
  node.instance().updateConfig('processDefinitionId', 'someId');

  expect(loadFrequencyData.mock.calls[0][0]).toBe('someId');
});

it('should load updated frequency data when the filter changed', () => {
  const node = mount(<Analysis />);

  node.instance().updateConfig('processDefinitionId', 'someId');
  loadFrequencyData.mockClear();
  node.instance().updateConfig('filter', ['someFilter'])

  expect(loadFrequencyData.mock.calls[0][1]).toEqual(['someFilter']);
});

it('should not try to load frequency data if no process definition is selected', () => {
  const node = mount(<Analysis />);

  loadFrequencyData.mockClear();
  node.instance().updateConfig('filter', ['someFilter'])

  expect(loadFrequencyData).not.toHaveBeenCalled();
});

it('should contain a statistics section if gateway and endEvent is selected', () => {
  const node = mount(<Analysis />);

  node.instance().setState({
    gateway: 'g',
    endEvent: 'e'
  });

  expect(node).toIncludeText('Statistics');
});
