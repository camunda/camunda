import {isDurationHeatmap, isProcessInstanceDuration} from './service';

it('Should correctly check for duration heatmap', () => {
  expect(
    isDurationHeatmap({
      view: {entity: 'flowNode', property: 'duration'},
      visualization: 'heat',
      processDefinitionKey: 'test',
      processDefinitionVersion: 'test'
    })
  ).toBeTruthy();
});

it('should correclty check for process instance duration reports', () => {
  expect(
    isProcessInstanceDuration({view: {entity: 'processInstance', property: 'duration'}})
  ).toBeTruthy();
});
