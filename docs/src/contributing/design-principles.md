# Design Principles

This page describes the primary design principles which we have set forward when developing Zeebe. The content on this page may be interesting to you in case you want to get a better understanding of how Zeebe works internally and the properties that make it fast.

## Garbage free in the Data Path

The Zeebe broker is implemented using Java Programming Language and runs on top of the JVM. Java is a _Garbage Collected Language_ which makes it very fast and productive to program as developers do not need to worry about allocating and freeing memory manually. Instead, the JVM takes care of this.

## Lock free Algorithms in the Data Path

Batching for I/O operations

Single Writer principle for I/O operations

Actor-style concurrency

Efficient Protocols of Interaction

