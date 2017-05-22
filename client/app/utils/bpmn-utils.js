export function isBpmnType(element, types) {
  if (typeof types === 'string') {
    types = [types];
  }
  return element.type !== 'label' &&
    types.filter(type => element.businessObject.$instanceOf('bpmn:' + type)).length > 0;
}

export function removeOverlays(viewer) {
  viewer.get('overlays').clear();
}
