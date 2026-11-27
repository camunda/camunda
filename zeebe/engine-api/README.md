# Workflow engine API

The in-process API between the broker and the workflow engine. It is not
the engine's external API, which stays the records in `zeebe-protocol`.

## Rule

The broker must not depend on the engine at compile scope. It compiles
against this module only, so that engine changes do not recompile the
broker and broker code cannot reach engine internals. The engine
implements the API. The application that assembles the broker (`dist`)
depends on the engine and injects the implementation. Tests that need a
running engine depend on it at test scope.

## What belongs here

A type belongs here only if the broker, or another module that must not
depend on the engine, needs it, and it does not depend on engine
internals. Everything else stays in the engine.

If a type needs engine internals, keep it in the engine and expose the
behavior through an interface here instead.

## Kinds of types

- Entry points through which the broker creates and drives the engine,
  and the values passed across that boundary.
- Configuration the broker or the configuration module sets for the
  engine.
- Service interfaces the broker implements and the engine calls, for
  example to stream jobs or to read data from the broker.

