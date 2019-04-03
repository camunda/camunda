/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
