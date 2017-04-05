export function isBpmnType(element, type) {
  return element.businessObject.$instanceOf('bpmn:' + type) && element.type !== 'label';
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
