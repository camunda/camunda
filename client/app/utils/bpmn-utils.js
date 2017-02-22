export function is(element, type) {
  return element.businessObject.$instanceOf('bpmn:' + type) && element.type !== 'label';
}
