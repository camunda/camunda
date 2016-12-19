export function triggerEvent({node, eventName, selector, properties, eventInit = {cancelable: true}}) {
  const event = new Event(eventName, eventInit);
  const target = typeof selector === 'string' ? node.querySelector(selector) : node;

  if (properties) {
    Object
      .keys(properties)
      .forEach((key) => {
        event[key] = properties[key];
      });
  }

  target.dispatchEvent(event);

  return event;
}
