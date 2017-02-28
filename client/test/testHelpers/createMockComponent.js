import {jsx, Children, createReferenceComponent} from 'view-utils';
import sinon from 'sinon';
import {observeFunction} from  './observeFunction';

export function createMockComponent(text, applyChildren) {
  const update = sinon.spy();
  const jsxTemplate = <div>{text}</div>;

  const template = observeFunction((node, eventsBus) => {
    return [
      update,
      jsxTemplate(node, eventsBus)
    ];
  });

  const constructedTemplates = [];
  const constructor = observeFunction(
    (attributes) => {
      const Reference = createReferenceComponent();
      const childrenTemplate = <div>
        <Reference name="children" />
        <Children children={attributes.children} />
      </div>;
      const constructedTemplate = (node, eventsBus) => {
        if (!applyChildren) {
          return template(node, eventsBus);
        }

        return [
          template(node, eventsBus),
          childrenTemplate(node, eventsBus)
        ];
      };

      constructedTemplate.attributes = attributes;
      constructedTemplate.text = text;
      constructedTemplate.getChildrenNode = () => Reference.getNode('children');

      constructedTemplates.push(constructedTemplate);

      return constructedTemplate;
    }
  );

  constructor.set('template', template);
  constructor.set('update', update);

  constructor.getEventsBus = (index) => {
    return template.calls[index][1];
  };

  constructor.getAttribute = withIndex(2, (attribute, index = 0) => {
    return constructor.calls[index][0][attribute];
  });

  constructor.getChildTemplate = withIndex(2, (predicate, index = 0) => {
    return constructor
      .getAttribute('children', index)
      .filter(
        buildPredicateFunction(predicate)
      );
  });

  constructor.getChildrenNode = withIndex(1, (index = 0) => {
    return constructedTemplates[index].getChildrenNode();
  });

  constructor.text = text;

  return constructor;

  function withIndex(arity, method) {
    return (...args) => {
      const lastArg = args[args.length - 1];

      if (args.length < arity || typeof lastArg === 'number') {
        return method(...args);
      }

      const index = findIndex(lastArg);

      return method(...args.slice(0, args.length - 1), index);
    };
  }

  function findIndex(predicate) {
    const predicateFn = buildPredicateFunction(predicate);

    for (let i = 0; i < constructor.calls.length; i++) {
      const attributes = constructor.calls[i][0];

      if (predicateFn(attributes)) {
        return i;
      }
    }

    throw new Error('Could not find index matching given predicate');
  }
}

function buildPredicateFunction(predicate) {
  if (typeof predicate === 'object') {
    return (attributes) => {
      return Object
        .keys(predicate)
        .reduce((result, key) => {
          return result && attributes[key] === predicate[key];
        }, true);
    };
  }

  return predicate;
}
