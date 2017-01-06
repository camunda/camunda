import {createEventsBus} from './events';
import {$document} from './dom';

export function jsx(element, attributes, ...children) {
  if (typeof element === 'function') {
    return handleComponent(element, attributes, children)
  }

  return handleHtml(element, attributes, children);
}

function handleComponent(component, attributes, children) {
  return component({
    children,
    ...attributes
  });
}

function handleHtml(element, attributes, children) {
  return (node, eventsBus) => {
    const elementNode = $document.createElement(element);

    node.appendChild(elementNode);

    if (attributes) {
      setAttributes(elementNode, attributes);
    }

    return addChildren(elementNode, eventsBus, children)
  };
}

function setAttributes(elementNode, attributes) {
  Object
    .keys(attributes)
    .forEach((attribute) => {
      const value = attributes[attribute];

      if (isValidAttributeValue(value)) {
        setAttribute(elementNode, attribute, value);
      }
    });
}

function isValidAttributeValue(value) {
  return typeof value === 'string' || typeof value === 'number';
}

function setAttribute(elementNode, attribute, value) {
  if (attribute === 'className') {
    return elementNode.setAttribute('class', value);
  }

  elementNode.setAttribute(attribute, value);
}

export function addChildren(elementNode, eventsBus, children) {
  return children.reduce((updates, child) => {
    return updates.concat(addChild(elementNode, eventsBus, child));
  }, []);
}

export function addChild(elementNode, eventsBus, child) {
  if (typeof child === 'string') {
    elementNode.appendChild(
      $document.createTextNode(child)
    );

    return [];
  }

  const childEventBus = createEventsBus(eventsBus);

  return { //TODO: make this optional, because it is used only in List
    update: child(elementNode, childEventBus),
    eventsBus: childEventBus
  };
}
