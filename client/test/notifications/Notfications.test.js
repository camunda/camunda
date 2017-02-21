import {expect} from 'chai';
import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {Notifications} from 'notifications/Notifications';

describe('<Notifications>', () => {
  let node;
  let update;

  beforeEach(() => {
    ({node, update} = mountTemplate(<Notifications selector="notifications"/>));
  });

  it('should display notification panel', () => {
    expect(node.querySelector('.notifications-panel')).to.exist;
  });

  it('should display list of notifications after update', () => {
    const state = {
      notifications: [
        {
          status: 'some status',
          text: 'some text'
        },
        {
          status: 'other status',
          text: 'other text'
        }
      ]
    };

    update(state);

    expect(node).to.contain.text('some status');
    expect(node).to.contain.text('some text');
    expect(node).to.contain.text('other status');
    expect(node).to.contain.text('other text');
  });
});
