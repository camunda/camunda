export function isBpmnType(element, type) {
  return element.businessObject.$instanceOf('bpmn:' + type) && element.type !== 'label';
}
