# Zeebe Transport

Simple asynchronous Message Transport over TCP/IP.

**Features**

* Multi-Protocol:
    * Single Message
    * Request-Response
* Asynchronous
* Pipelining for high throughput (many interactions can share single TCP connection)

* [Web Site](https://zeebe.io)
* [Documentation](https://docs.zeebe.io)
* [Issue Tracker](https://github.com/zeebe-io/zeebe/issues)
* [Slack Channel](https://zeebe-slack-invite.herokuapp.com/)
* [User Forum](https://forum.zeebe.io)
* [Contribution Guidelines](/CONTRIBUTING.md)
* Connection Management (reconnect, keep-alive)

## Framing & Pipelining

zb-transport implements simple message framing. A frame is a header which is put on every message and most importantly defined the length of the message:

```
+---------------------------------------------------------------+
|                        Message Length                         |
+---------------------------------------------------------------+
|                   [other header properties]                   |
+---------------------------------------------------------------+
|                                                               |
|                            Message                          ...
...                                                             |
+---------------------------------------------------------------+
```

This way, multiple independent messages can be streamed through a single TCP channel:

```
-----------------------------------------------------------------
|   Message 1  |   Message 2  |  Message 3  |  Message 4  |  ...
-----------------------------------------------------------------
```

The pipelining allows using TCP effectively: potentially many messages can be packed into a single TCP segment.


## Single Message Protocol

This protocol allows sending of single, unrelated messages.

## Request-Response

The request response-protocol protocol provides a simple means correlating messages which represent a request from a client to a server to a reply message which represents a response.

