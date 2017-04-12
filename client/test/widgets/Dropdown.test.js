import {jsx, Socket} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Dropdown, DropdownItem, __set__, __ResetDependency__} from 'widgets/Dropdown';

describe('<Dropdown>', () => {
  let update;
  let node;
  let $;
  let dropdownFct;

  beforeEach(() => {
    dropdownFct = sinon.spy();
    $ = sinon.stub().returns({
      dropdown: dropdownFct
    });

    __set__('$', $);

    ({update, node} = mountTemplate(
      <Dropdown>
        <Socket name="label"><span className="label">Label</span></Socket>
        <Socket name="list"><span className="list">Label</span></Socket>
      </Dropdown>
    ));
    update({});
  });

  afterEach(() => {
    __ResetDependency__('$');
  });

  it('should call the dropdown initializer', () => {
    expect(dropdownFct.calledOnce).to.eql(true);
  });

  it('should put the label in a button', () => {
    const label = node.querySelector('.label');

    expect(label).to.exist;
    expect(label.parentNode.tagName).to.eql('BUTTON');
  });

  it('should contain an arrow to indicate that it is a dropdown', () => {
    const arrow = node.querySelector('.caret');

    expect(arrow).to.exist;
  });

  it('should put the list in an ul', () => {
    const list = node.querySelector('.list');

    expect(list).to.exist;
    expect(list.parentNode.tagName).to.eql('UL');
  });
});

describe('<DropdownItem>', () => {
  let update;
  let node;
  let clickSpy;

  beforeEach(() => {
    clickSpy = sinon.spy();

    ({update, node} = mountTemplate(
      <DropdownItem listener={clickSpy}>
        <span className="item">Item</span>
      </DropdownItem>
    ));
    update({});
  });

  it('should wrap the content', () => {
    const item = node.querySelector('.item');

    expect(item).to.exist;
    expect(item.parentNode.tagName).to.eql('A');
    expect(item.parentNode.parentNode.tagName).to.eql('LI');
  });

  it('should call the provided function on click', () => {
    triggerEvent({
      node,
      selector: 'a',
      eventName: 'click'
    });

    expect(clickSpy.calledOnce).to.eql(true);
  });
});
