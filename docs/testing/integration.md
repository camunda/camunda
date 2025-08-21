# Integration tests

Integration tests (ITs) cover verifies the integration of two or more units or components together.

**Key properties:**

* Tests multiple units at once
* Possibly tests with dependencies (e.g. a database, another module)
* Slower than unit, but still fast
* Live within the module where the units under tests are (e.g. exporter ITs live in the exporter module)
* Set up and tear down is more complex, often slower, so reuse is encouraged (e.g. static DB containers,
  reusable helper classes to generate state, etc.)

> [!Note]
>
> The key properties are rule of thumb, we should try to stick with it, there might be cases to
> break with this.

## Testing multiple units

It's important to test the interaction between multiple components or units to ensure they work
together as expected, in order to trigger multiple code paths.

Think about building a bridge from both sides - it's clear how to extend the bridge, and each
individual steps might be validated, but we need to make sure it meets in the middle. If it doesn't,
then the complete bridge is a failure. See the Coppenhagen Kissing Bridge as an example below.

![bride-fail](assets/bridge-fail.png)
_Lloyd Alter/ Inderhavnsbroen/[CC BY 2.0](https://creativecommons.org/licenses/by/2.0/deed.en)_


