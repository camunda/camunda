# Performance

Zeebe is designed for performance, applying the following design principles:

* Batching of I/O operations
* Linear read/write data access patterns
* Compact, cache-optimized data structures
* Lock-free algorithms and actor concurrency (green threads model)
* Broker is garbage-free in the hot/data path

As a result, Zeebe is capable of very high throughput on a single node and scales horizontally (see this [benchmarking blog post](https://zeebe.io/blog/2018/06/benchmarking-zeebe-horizontal-scaling/) for more detail). 
