import {extractDefinitionName} from './definitionService';

it('return defintionName when available', () => {
  const definitionName = extractDefinitionName(
    'leadQualification',
    `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <bpmn:process id="leadQualification" name='test'>
      </bpmn:process>
    </bpmn:definitions>`
  );
  expect(definitionName).toBe('test');
});

it('return defintionKey if name does not exist', () => {
  const definitionName = extractDefinitionName(
    'leadQualification',
    `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <bpmn:process id="leadQualification">
      </bpmn:process>    
    </bpmn:definitions>`
  );
  expect(definitionName).toBe('leadQualification');
});
