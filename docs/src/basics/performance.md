# Performance

Zeebe is designed for performance, applying the following design principles:

* Batching of I/O operations
* Linear read/write data access patterns
* Compact, cache-optimized data structures
* Lock-free algorithms and actor concurrency (green threads model)
* Broker is garbage-free in the hot/data path

In addition, using partitions as a scaling mechanism, Zeebe scales horizontally to achieve high throughput (without a relational database as a potential bottleneck).
