# Schema Migration Service for OpenSearch

## Issue with the "append" Processor

While integrating the schema migration service with OpenSearch, we encountered a limitation with the "append" processor.
When using the `opensearch-java` library, attempting to use the "append" processor with a non-empty array of any type
results in an `UnsupportedOperationException`.

### Workarounds

Given this limitation, users who wish to achieve the functionality provided by the "append" processor can utilize
the following alternatives:

1. **Set Processor**: The `set` processor can be used to set or update values. While it doesn't inherently have
   the "append" functionality, in combination with existing values, it can be used to set the initial or rewrite existing values.

2. **Script Processor**: More complex and flexible than the `set` processor, the `script` processor allows you
   to manipulate data in various ways. Users familiar with OpenSearch's scripting can use this processor to dynamically
   append values to existing fields.

It's essential to understand the specific needs of your migration when deciding between these workarounds.
If you're merely setting or updating values, the `set` processor may suffice.
However, for more intricate data manipulations, you might need to rely on the `script` processor.

## Further Updates

We will continue to monitor any updates to the `opensearch-java` library and make adjustments as necessary.
