import {$document, addClass, removeClass, noop} from 'view-utils';
import {getModule} from './registry.service';

export function DynamicLoader({module, ...props}) {
  return (node, eventsBus) => {
    const target = $document.createElement('div');
    let update = noop;

    node.appendChild(target);

    addClass(target, 'loader loading');

    getModule(module)
      .then(({component}) => {
        const template = component(props);

        removeClass(target, 'loader loading');
        update = template(target, eventsBus);
      })
      .catch((error) => {
        error = true;

        removeClass(target, 'loading');
        addClass(target, 'loader-error');

        target.innerHTML = `Could not load ${module} module`;

        throw error;
      });

    return (state) => update(state);
  }
}
