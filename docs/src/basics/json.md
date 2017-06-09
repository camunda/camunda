# Json

Zeebe uses Json for:

* user-provided payload,
* internal data,
* API and protocol messages.



## Message Pack

For performance reasons, Json is represented using message pack which is a binary representation of Json. Using message pack allows the broker to traverse a Json document on the binary level without interpreting it as a string and without needing to "parse" it.

As a user, you do not need to deal with message pack directly. The clients take care of converting between message pack and Json.

