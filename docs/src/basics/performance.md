# Performance

Zeebe is designed for performance, applying the following design principles:

* Batching of I/O operations
* Linear read/write data access patterns
* Compact, cache-optimized data structures
* Lock-free algorithms and actor concurrency (green threads model)
* Broker is garbage-free in the hot/data path

As a result, Zeebe is capable of processing events at a rate of 160K – 200K events/s on modern SSDs & Gbit Ethernet.

Note however: there are some performance regressions in the current development phase which are planned to be improved in the near future.
