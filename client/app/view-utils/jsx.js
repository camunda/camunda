import {createEventsBus} from './events';
import {$document} from './dom';
import {flatten} from './flatten';

export function jsx(element, attributes, ...children) {
  const flatChildren = flatten(children);

  if (typeof element === 'function') {
    return handleComponent(element, attributes, flatChildren);
  }

  return handleHtml(element, attributes, flatChildren);
}

function handleComponent(component, attributes, children) {
  return component({
    children,
    ...attributes
  });
}

function handleHtml(element, attributes, children) {
  const template = (node, eventsBus) => {
    const elementNode = $document.createElement(element);

    node.appendChild(elementNode);

    if (attributes) {
      setAttributes(elementNode, attributes);
    }

    return addChildren(elementNode, eventsBus, children);
  };

  //For debuging only, so disabled in production
  if (process.env.NODE_ENV !== 'production') {
    addErrorProperty(template, 'element', element);
    addErrorProperty(template, 'attributes', attributes);
    addErrorProperty(template, 'children', children);
  }

  return template;
}

function addErrorProperty(object, name, value) {
  Object.defineProperty(object, name, {
    get: () => {
      // eslint-disable-next-line
      console.error(`"${name}" propety of template is available only in development settings. Please remove any usage of it.`);

      return value;
    }
  });
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

export function addChildren(elementNode, eventsBus, children, shouldAddEventsBus) {
  return children.reduce((updates, child) => {
    return updates.concat(addChild(elementNode, eventsBus, child, shouldAddEventsBus));
  }, []);
}

export function addChild(elementNode, eventsBus, child, shouldAddEventsBus) {
  if (typeof child === 'string') {
    elementNode.appendChild(
      $document.createTextNode(child)
    );

    return [];
  }

  const childEventBus = createEventsBus(eventsBus);
  const update = child(elementNode, childEventBus);

  if (shouldAddEventsBus) {
    return {
      update,
      eventsBus: childEventBus
    };
  }

  return update;
}
