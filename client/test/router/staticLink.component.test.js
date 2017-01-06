import {expect} from 'chai';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {StaticLink, __set__, __ResetDependency__} from 'router/staticLink.component';

describe('<StaticLink>', () => {
  const name = 'name1';
  let router;
  let params;
  let node;

  beforeEach(() => {
    router = {
      getUrl: sinon.stub().returnsArg(0),
      goTo: sinon.spy()
    };

    __set__('router', router);

    params = {a: 1};

    ({node} = mountTemplate(<a>
      <StaticLink name={name} params={params} />
    </a>));
  });

  afterEach(() => {
    __ResetDependency__('router');
  });

  it('should set href on a tag', () => {
    expect(node.querySelector('a')).to.have.attr('href', name);
  });

  it('should prevent default click behavior', () => {
    const event = triggerEvent({
      node,
      selector: 'a',
      eventName: 'click'
    });

    expect(event.defaultPrevented).to.eql(true);
  });

  it('should go to route on click', () => {
    triggerEvent({
      node,
      selector: 'a',
      eventName: 'click'
    });

    expect(router.goTo.calledWith(name, params, false))
      .to.eql(true, 'expected router.goTo called with name and params');
  });

  it('should replace route on click', () => {
    ({node} = mountTemplate(<a>
      <StaticLink name={name} params={params} replace={true}/>
    </a>));

    triggerEvent({
      node,
      selector: 'a',
      eventName: 'click'
    });

    expect(router.goTo.calledWith(name, params, true))
      .to.eql(true, 'expected router.goTo called with name and params and replace');
  });
});
