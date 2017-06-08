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

  let constructedTemplates = [];
  let appliedCalls = [];
  const constructoringFn = observeFunction(
    (attributes) => {
      const Reference = createReferenceComponent();
      const childrenTemplate = <div>
        <Reference name="children" />
        <Children children={attributes.children} />
      </div>;
      const constructedTemplate = (node, eventsBus) => {
        appliedCalls.push(attributes);

        if (!applyChildren) {
          return template(node, eventsBus);
        }

        return [
          template(node, eventsBus),
          childrenTemplate(node, eventsBus)
        ];
      };

      constructedTemplate.getChildrenNode = () => Reference.getNode('children');

      constructedTemplates.push(constructedTemplate);

      return constructedTemplate;
    }
  );

  constructoringFn.set('template', template);
  constructoringFn.set('update', update);

  constructoringFn.getEventsBus = withIndex(
    {
      arity: 1,
      calls: template.calls
    },
    (index = 0) => {
      return template.calls[index][1];
    }
  );

  constructoringFn.getAttribute = withIndex({arity: 2}, (attribute, index = 0) => {
    return constructoringFn.calls[index][0][attribute];
  });

  constructoringFn.getChildTemplate = withIndex({arity: 2}, (predicate, index = 0) => {
    return constructoringFn
      .getAttribute('children', index)
      .filter(
        buildPredicateFunction(predicate)
      );
  });

  constructoringFn.getChildrenNode = withIndex({arity: 1}, (index = 0) => {
    return constructedTemplates[index].getChildrenNode();
  });

  constructoringFn.appliedWith = (predicate) => {
    return callsUsedWith(predicate, appliedCalls);
  };

  constructoringFn.updatedWith = (predicate) => {
    const calls = [];

    for (let i = 0; i < update.callCount; i++) {
      calls.push(update.getCall(i).args[0]);
    }

    return callsUsedWith(predicate, calls);
  };

  function callsUsedWith(predicate, calls) {
    const predicateFn = buildPredicateFunction(predicate);

    return calls.filter(predicateFn).length > 0;
  }

  constructoringFn.reset = () => {
    constructoringFn.calls = [];
    constructedTemplates = [];
    appliedCalls = [];

    template.reset();
    update.reset();
  };

  constructoringFn.text = text;

  return constructoringFn;

  function withIndex({arity, calls = constructoringFn.calls, throwOnFail = true}, method) {
    return (...args) => {
      const lastArg = args[args.length - 1];

      if (args.length < arity || typeof lastArg === 'number') {
        return method(...args);
      }

      const index = findIndex(lastArg, calls, throwOnFail);

      return method(...args.slice(0, args.length - 1), index);
    };
  }

  function findIndex(predicate, calls, throwOnFail) {
    const predicateFn = buildPredicateFunction(predicate);

    for (let i = 0; i < calls.length; i++) {
      const attributes = calls[i][0];

      if (predicateFn(attributes)) {
        return i;
      }
    }

    if (throwOnFail) {
      throw new Error('Could not find index matching given predicate');
    }
  }
}

function buildPredicateFunction(predicate) {
  if (typeof predicate === 'object') {
    return attributes => {
      return Object
        .keys(predicate)
        .reduce((result, key) => {
          return result && attributes[key] === predicate[key];
        }, true);
    };
  }

  if (typeof predicate !== 'function') {
    return state => state === predicate;
  }

  return predicate;
}
