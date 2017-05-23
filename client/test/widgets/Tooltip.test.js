import {jsx} from 'view-utils';
import {expect} from 'chai';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {Tooltip} from 'widgets/Tooltip';

describe('<Tooltip>', () => {
  const text = 'tooltip text';
  let node;

  beforeEach(() => {
    ({node} = mountTemplate(<Tooltip>{text}</Tooltip>));
  });

  it('should not display tooltip by default', () => {
    expect(document.body).not.to.contain.text(text);
  });

  it('should display text on hover', () => {
    triggerEvent({
      node,
      eventName: 'mouseenter'
    });

    expect(document.body).to.contain.text(text);

    triggerEvent({
      node,
      eventName: 'mouseleave'
    });

    expect(document.body).not.to.contain.text(text);
  });
});
