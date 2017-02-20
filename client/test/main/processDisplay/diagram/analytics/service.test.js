import {expect} from 'chai';
import {clickElement} from 'main/processDisplay/diagram/analytics/service';

describe('Analytics service', () => {
  let endEvent;
  let otherNode;
  let state;
  let nodes;

  beforeEach(() => {
    endEvent = {
      type: 'bpmn:EndEvent',
      name: 'End Event',
      id: 'act1'
    };
    otherNode = {
      type: 'bpmn:Task',
      name: 'Some Task',
      id: 'act2'
    };

    state = {heatmap: {
      data: {
        act1: 1,
        act2: 2
      }
    }};

    nodes = {
      name: {
        textContent: ''
      },
      counter: {
        textContent: ''
      }
    };
  });

  describe('click elements', () => {
    it('should do nothing and return false if the element is not an end event', () => {
      const returnValue = clickElement(otherNode, state, nodes);

      expect(returnValue).to.eql(false);
      expect(nodes.name.textContent).to.eql('');
      expect(nodes.counter.textContent).to.eql('');
    });

    it('should set the name node contents to the element name or id', () => {
      const returnValue = clickElement(endEvent, state, nodes);

      expect(returnValue).to.eql(true);
      expect(nodes.name.textContent).to.eql('End Event');
    });

    it('should set the counter node contents to the correct percentage value', () => {
      clickElement(endEvent, state, nodes);

      expect(nodes.counter.textContent).to.eql(50);
    });

    it('should should round the counter value', () => {
      state.heatmap.data.act2 = 3;
      clickElement(endEvent, state, nodes);

      expect(nodes.counter.textContent).to.eql(33.3);
    });
  });
});
