import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {StatisticChart, __set__, __ResetDependency__} from 'main/processDisplay/views/analytics/statistics/StatisticChart';
import React from 'react';
import {mount} from 'enzyme';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<StatisticChart>', () => {
  let node;
  let Chart;
  let isLoaded;
  let data;

  beforeEach(() => {
    Chart = createReactMock('Chart');
    __set__('Chart', Chart);

    isLoaded = sinon.stub();
    __set__('isLoaded', isLoaded);

    data = sinon.stub().returns([]);
  });

  afterEach(() => {
    __ResetDependency__('Chart');
    __ResetDependency__('isLoaded');
  });

  describe('loading', () => {
    beforeEach(() => {
      isLoaded.returns(false);

      node = mount(<StatisticChart
        data={data}
        chartConfig={{}}
        correlation={{}}
        selection={{}}
        height={{}}
      >some header text</StatisticChart>);
    });

    it('should display a loading indicator if data is loading', () => {
      expect(node.find('.loading_indicator')).to.be.present();
      expect(node.find('.chart')).to.not.be.present();
    });
  });

  describe('loaded', () => {
    beforeEach(() => {
      isLoaded.returns(true);

      node = mount(<StatisticChart
        data={data}
        chartConfig={{}}
        correlation={{}}
        selection={{}}
        height={{}}
      >some header text</StatisticChart>);
    });

    it('should contain a header', () => {
      expect(node.find('.chart-header')).to.be.present();
    });

    it('should contain the child content in the header text', () => {
      expect(node.find('.chart-header')).to.contain.text('some header text');
    });

    it('should display a chart', () => {
      expect(node.find('.chart')).to.be.present();
    });

    it('should show a no data indicator if there is no data', () => {
      expect(node.find('.no-data-indicator')).to.be.present();
    });
  });
});
