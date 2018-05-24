# JSON

## Message Pack

For performance reasons JSON is encoded using [MessagePack](https://msgpack.org/). MessagePack allows the broker to traverse a JSON document on the binary level without interpreting it as text and without need for complex parsing.

As a user, you do not need to deal with MessagePack. The client libraries take care of converting between MessagePack and JSON.
