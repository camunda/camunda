import {$document, addClass, removeClass, noop, runUpdate, dispatchAction} from 'view-utils';
import {getModule} from './registry.service';

export function DynamicLoader({module, ...props}) {
  let componet;

  return (node, eventsBus) => {
    const target = $document.createElement('div');
    let update = noop;

    node.appendChild(target);

    if (!componet) {
      addClass(target, 'loader loading');
      fetchComponent(target, eventsBus)
        .then(_update => {
          update = _update;

          dispatchAction({
            type: '@@LOADED',
            module
          });
        })
    } else {
      update = applyComponent(componet, target, eventsBus);
    }

    return (state) => {
      runUpdate(update, state);
    }
  };

  function fetchComponent(target, eventsBus) {
    return getModule(module)
      .then(({component: _component}) => {
        componet = _component;

        return applyComponent(componet, target, eventsBus);
      })
      .catch((error) => {
        removeClass(target, 'loading');
        addClass(target, 'loader-error');

        target.innerHTML = `Could not load ${module} module`;

        throw error;
      });
  }

  function applyComponent(component, target, eventsBus) {
    const template = component(props);

    removeClass(target, 'loader loading');
    return template(target, eventsBus);
  }
}
