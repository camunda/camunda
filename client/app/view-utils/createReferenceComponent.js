import {DESTROY_EVENT} from './events';

export function createReferenceComponent(target) {
  return ({name}) => {
    return (node, eventsBus) => {
      target[name] = node;

      eventsBus.on(DESTROY_EVENT, () => {
        delete target[name];
      });

      return [];
    };
  };
}
