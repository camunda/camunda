import {$document} from './dom';

// This function is a bit hacky. Well that is very delicate description.
// It suppose to act as proxy to parent node with few limitations
// Those limitations should be less troublesome than using document fragment anyway
// It might be a bit slower than document fragment thought.
// So there is some tradeoff to using it.
// Main limitation is parent node cannot be removed or moved using proxy node.
// It should not matter greatly anyway.
// This probably never be part of public API.
// Also there are probably few parts of DOM API related to childs manipulation
// that are not yet supported in proxy node
// in way that would be intuitive, but that can be implemented when need arrises
// Also it maybe helpful to use MutationObserver to achieve better compatibility
export function createProxyNode(parent, startMarker) {
  // Using detached node as proxy in hope that there will be no error related to
  // wrong type passed to some native function. Although that may not be desired.
  let currentStartMarker = startMarker;
  let childNodes = [];
  const proxyNode = $document.createElement(parent.tagName);
  const descriptors = {};

  proxyNode.setStartMarker = (startMarker) => currentStartMarker = startMarker;

  // Not using Object.keys, because prototype properties need to be proxied too
  for (const property in parent) {
    const value = parent[property];

    if (typeof value === 'function') {
      descriptors[property] = {
        enumerable: true,
        configurable: false,
        writable: false,
        value: value.bind(parent)
      };
    } else {
      descriptors[property] = {
        enumerable: true,
        configurable: false,
        get: () => parent[property],
        set: value => parent[property] = value
      };
    }
  }

  descriptors.childNodes = {
    enumerable: true,
    configurable: false,
    // Another limitation childNodes is not live array like element
    get: () => childNodes.slice()
  };

  descriptors.appendChild = {
    enumerable: true,
    configurable: false,
    // Another limitation childNodes is not live array like element
    value: (child) => {
      const lastNode = childNodes[childNodes.length - 1] || currentStartMarker;

      parent.insertBefore(child, lastNode.nextSibling);
      childNodes.push(child);
    }
  };
  descriptors.append = descriptors.appendChild;

  descriptors.removeChild = {
    enumerable: true,
    configurable: false,
    // Another limitation childNodes is not live array like element
    value: (child) => {
      parent.removeChild(child);

      childNodes = childNodes.filter(other => other !== child);
    }
  };
  descriptors.remove = descriptors.removeChild;

  descriptors.insertBefore = {
    enumerable: true,
    configurable: false,
    value: (child, target) => {
      const index = childNodes.indexOf(target);

      if (index >= 0) {
        childNodes.splice(index, 0, child);
      } else {
        childNodes.push(child);
      }

      parent.insertBefore(child, target);
    }
  };

  descriptors.removeChildren = {
    enumerable: true,
    configurable: false,
    // Another limitation childNodes is not live array like element
    value: () => {
      const deatachedNodes = childNodes;

      childNodes.forEach(child => parent.removeChild(child));
      childNodes = [];

      return deatachedNodes;
    }
  };

  descriptors.replaceChild = {
    enumerable: true,
    configurable: false,
    value: (newChild, oldChild) => {
      childNodes = childNodes.map(child => {
        if (child === oldChild) {
          return newChild;
        }

        return child;
      });

      parent.replaceChild(newChild, oldChild);
    }
  };

  Object.defineProperties(proxyNode, descriptors);

  return proxyNode;
}
