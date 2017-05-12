import {$window} from './dom';

const isProxySupported = typeof $window.Proxy === 'function';

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
  let currentStartMarker = startMarker;
  let childNodes = [];

  const proxyMethods = {
    setStartMarker: (startMarker) => currentStartMarker = startMarker,
    appendChild: (child) => {
      const lastNode = childNodes[childNodes.length - 1] || currentStartMarker;

      parent.insertBefore(child, lastNode.nextSibling);
      childNodes.push(child);
    },
    append: aliasMethod('appendChild'),
    removeChild: (child) => {
      parent.removeChild(child);

      childNodes = childNodes.filter(other => other !== child);
    },
    remove: aliasMethod('removeChild'),
    insertBefore: (child, target) => {
      const index = childNodes.indexOf(target);

      if (index >= 0) {
        childNodes.splice(index, 0, child);
      } else {
        childNodes.push(child);
      }

      parent.insertBefore(child, target);
    },
    removeChildren: () => {
      const deatachedNodes = childNodes;

      childNodes.forEach(child => parent.removeChild(child));
      childNodes = [];

      return deatachedNodes;
    },
    replaceChild: (newChild, oldChild) => {
      childNodes = childNodes.map(child => {
        if (child === oldChild) {
          return newChild;
        }

        return child;
      });

      parent.replaceChild(newChild, oldChild);
    }
  };

  const proxyProperties = {
    childNodes: () => childNodes.slice()
  };

  const proxyNode = isProxySupported ?
    createWithProxyTrap(parent, proxyProperties, proxyMethods) :
    createWithIteration(parent, proxyProperties, proxyMethods);

  return proxyNode;

  function aliasMethod(target) {
    return (...args) => proxyMethods[target](...args);
  }
}

function createWithProxyTrap(parent, proxyProperties, proxyMethods) {
  return new Proxy(parent, {
    get: (obj, prop) => {
      if (proxyProperties[prop]) {
        return proxyProperties[prop]();
      }

      if (proxyMethods[prop]) {
        return proxyMethods[prop];
      }

      const value = obj[prop];

      if (typeof value === 'function') {
        return value.bind(obj);
      }

      return value;
    },
    set: (obj, prop, value) => {
      if (proxyProperties[prop] || proxyMethods[prop]) {
        return;
      }

      obj[prop] = value;
    }
  });
}

function createWithIteration(parent, proxyProperties, proxyMethods) {
  const descriptors = {};
  const proxyNode = {};

  proxyNode.setStartMarker = proxyMethods.setStartMarker;
  proxyNode.removeChildren = proxyMethods.removeChildren;

  // Not using Object.keys, because prototype properties need to be proxied too
  for (const property in parent) {
    const value = parent[property];

    if (typeof value === 'function') {
      descriptors[property] = {
        enumerable: true,
        configurable: false,
        writable: false,
        value: proxyMethods[property] ? proxyMethods[property] : value.bind(parent)
      };
    } else {
      descriptors[property] = {
        enumerable: true,
        configurable: false,
        get: () => proxyProperties[property] ? proxyProperties[property] : parent[property],
        set: value => parent[property] = value
      };
    }
  }

  Object.defineProperties(proxyNode, descriptors);

  return proxyNode;
}
