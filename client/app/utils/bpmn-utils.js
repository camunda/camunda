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

export function updateOverlayVisibility(viewer, element, type) {
  // hide all hovered elements
  viewer
    .get('overlays')
    .get({type})
    .forEach((node) => {
      if (!node.keepOpen) {
        node.html.style.display = 'none';
      }
    });

  // display the new hovered element
  const node = viewer.get('overlays').get({element, type})[0];

  if (node) {
    node.html.style.display = 'block';
  }
}
