Zeebe is currently in a development state that we call "tech preview". This means that

* Zeebe is under heavy development and refactoring
* The "initial" scope of the system has not been developed completely yet
* Zeebe is not production-ready

# Roadmap

The following high-level roadmap is meant to give you an idea of our plans for Zeebe and provide context on when you can expect
a certain level of maturity.

## Milestone 1

![Status: Done](https://img.shields.io/badge/state-done-brightgreen.svg?style=flat-square)

* Achieve Scalability through partitioning
    * Define topics with multiple partitions
    * Deploy workflows to multiple partitions
    * Partition-aware subscriptions

* BPMN Exclusive Gateway and Conditions

## Milestone 2

![Status: In Progress](https://img.shields.io/badge/state-in_progress-yellow.svg?style=flat-square)

* Achieve reliability in cluster setup
    * Support all partition features in cluster setup
    * Long-running operations test suite
    * Failure cases test suite

## Milestone 3

![Status: Not Started](https://img.shields.io/badge/state-not_started-lightgrey.svg?style=flat-square)

* Achieve production-readiness:
    * Checksums in data fragments to detect data corruption
    * Log compaction to reclaim disk space
    * Ability to update a running Zeebe cluster

## Milestone 4

![Status: Not Started](https://img.shields.io/badge/state-not_started-lightgrey.svg?style=flat-square)

* Expand BPMN Support:
    * Decision Task
    * Parallel Gateway
    * Message Catch Event
