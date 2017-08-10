Zeebe is currently in a development state that we call "tech preview". This means that

* Zeebe is under heavy development and refactoring
* The "initial" scope of the system has not been developed completely yet
* Zeebe is not production-ready

# Roadmap

The following high-level roadmap is meant to give you an idea of our plans for Zeebe and provide context on when you can expect
a certain level of maturity.

**Milestone 1**

* Achieve Scalability through partitioning
    * Define topics with multiple partitions 
    * Deploy workflows to multiple partitions
    * Partition-aware subscriptions
    
* BPMN Exclusive Gateway and Conditions

**Milestone 2**

* Achieve production-readiness:
    * Checksums in data fragments to detect data corruption
    * Log Compaction
    * Long-running operations testsuite
    * Failure cases testsuite
