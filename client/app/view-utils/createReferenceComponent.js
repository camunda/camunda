import {DESTROY_EVENT} from './events';

export function createReferenceComponent(target = {}) {
  const Reference =  ({name}) => {
    return (node, eventsBus) => {
      target[name] = node;

      eventsBus.on(DESTROY_EVENT, () => {
        delete target[name];
      });

      return [];
    };
  };

  Reference.getNode = (name) => target[name];

  return Reference;
}
