const Chart = jest.fn(() => {
  return {
    destroy: jest.fn()
  };
});

Chart.defaults = {};

Chart.controllers = {
  line: {
    extend: jest.fn()
  }
};

export default Chart;
