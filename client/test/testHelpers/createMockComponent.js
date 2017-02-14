import {jsx} from 'view-utils';
import sinon from 'sinon';
import {observeFunction} from  './observeFunction';

export function createMockComponent(text) {
  const update = sinon.spy();
  const jsxTemplate = <div>{text}</div>;

  const template = observeFunction((node, eventsBus) => {
    return [
      update,
      jsxTemplate(node, eventsBus)
    ];
  });

  const constructor = observeFunction(() => {
    return template;
  });

  constructor.set('template', template);
  constructor.set('update', update);

  constructor.getEventsBus = (index) => {
    return template.calls[index][1];
  };

  constructor.text = text;

  return constructor;
}
